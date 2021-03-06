package com.bnade.wow.v2.dao;

import com.bnade.wow.util.DBUtils;
import com.bnade.wow.util.TimeUtils;
import com.bnade.wow.v2.entity.*;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 拍卖数据的数据库操作
 * Created by liufeng0103@163.com on 2017/6/11.
 */
public class AuctionDao {

    private static final Logger logger = LoggerFactory.getLogger(AuctionDao.class);

    private static AuctionDao auctionDao;

    private QueryRunner runner = DBUtils.getQueryRunner();

    public static AuctionDao getInstance() {
        return auctionDao == null ? auctionDao = new AuctionDao() : auctionDao;
    }

    public List<Auction> findByRealmIdAndItemId(Integer realmId, Integer itemId) throws SQLException {
        return runner.query(
                "select auc,item_id as itemId,owner,owner_realm as ownerRealm,bid,buyout,quantity,time_left as timeLeft,pet_species_id as petSpeciesId,pet_level as petLevel,pet_breed_id as petBreedId,context,bonus_list as bonusList,realm_id as realmId from auction where realm_id=? and item_id=?",
                new BeanListHandler<Auction>(Auction.class), realmId, itemId);
    }

    /**
     * 保存拍卖数据
     * @param aucs 拍卖数据
     * @throws SQLException 数据库异常
     */
    public void save(List<Auction> aucs) throws SQLException {
        Connection con = DBUtils.getDataSource().getConnection();
        try {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            Object[][] params = new Object[aucs.size()][14];
            for (int i = 0; i < aucs.size(); i++) {
                Auction auc = aucs.get(i);
                params[i][0] = auc.getAuc();
                params[i][1] = auc.getItemId();
                params[i][2] = auc.getOwner();
                params[i][3] = auc.getOwnerRealm();
                params[i][4] = auc.getBid();
                params[i][5] = auc.getBuyout();
                params[i][6] = auc.getQuantity();
                params[i][7] = auc.getTimeLeft();
                params[i][8] = auc.getPetSpeciesId();
                params[i][9] = auc.getPetLevel();
                params[i][10] = auc.getPetBreedId();
                params[i][11] = auc.getContext();
                params[i][12] = auc.getBonusList();
                params[i][13] = auc.getRealmId();
            }
            runner.batch(con, "insert into auction (auc,item_id,owner,owner_realm,bid,buyout,quantity,time_left,pet_species_id,pet_level,pet_breed_id,context,bonus_list,realm_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)", params);
            con.commit();
            con.setAutoCommit(autoCommit);
        } finally {
            DbUtils.closeQuietly(con);
        }
    }

    /**
     * 删除某个服务器的所有拍卖数据
     * 由于auction使用的分区表，通过删除分区来快速删除所有数据
     * @param realmId 服务器id
     * @throws SQLException 数据库操作异常
     */
    public void deleteByRealmId(int realmId) throws SQLException {
        // 判断分区是否存在
        Object obj = runner.query("SELECT * FROM INFORMATION_SCHEMA.partitions WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_NAME='auction' and partition_name=?"
                , new BeanHandler<>(Object.class), "p" + realmId);
        if (obj == null) {
            logger.info("p{}分区不存在,创建该分区", realmId);
            runner.update(" ALTER TABLE auction ADD PARTITION (PARTITION p" + realmId + " VALUES IN (" + realmId + "))");
        } else {
            logger.info("删除并重新创建分区p{}", realmId);
            runner.update("ALTER TABLE auction DROP PARTITION p" + realmId);
            runner.update(" ALTER TABLE auction ADD PARTITION (PARTITION p" + realmId + " VALUES IN (" + realmId + "))");
        }
    }

    /**
     * 通过realm id删除这个服务器的所有最低一口价拍卖数据
     * @param realmId 服务器id
     * @throws SQLException 数据库异常
     */
    public void deleteLowestByRealmId(int realmId) throws SQLException {
        runner.update("DELETE FROM cheapest_auction where realm_id=?", realmId);
    }

    /**
     * 保存最低一口价拍卖数据
     * @param aucs 拍卖数据
     * @throws SQLException 数据库异常
     */
    public void saveLowest(List<CheapestAuction> aucs) throws SQLException {
        Connection con = DBUtils.getDataSource().getConnection();
        try {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            Object[][] params = new Object[aucs.size()][15];
            for (int i = 0; i < aucs.size(); i++) {
                CheapestAuction auc = aucs.get(i);
                params[i][0] = auc.getAuc();
                params[i][1] = auc.getItemId();
                params[i][2] = auc.getOwner();
                params[i][3] = auc.getOwnerRealm();
                params[i][4] = auc.getBid();
                params[i][5] = auc.getBuyout();
                params[i][6] = auc.getQuantity();
                params[i][7] = auc.getTotalQuantity();
                params[i][8] = auc.getTimeLeft();
                params[i][9] = auc.getPetSpeciesId();
                params[i][10] = auc.getPetLevel();
                params[i][11] = auc.getPetBreedId();
                params[i][12] = auc.getContext();
                params[i][13] = auc.getBonusList();
                params[i][14] = auc.getRealmId();
            }
            runner.batch(con,
                    "insert into cheapest_auction (auc,item_id,owner,owner_realm,bid,buyout,quantity,total_quantity,time_left,pet_species_id,pet_level,pet_breed_id,context,bonus_list,realm_id) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    params);
            con.commit();
            con.setAutoCommit(autoCommit);
        } finally {
            DbUtils.closeQuietly(con);
        }
    }

    /**
     * 拷贝服务器当前最低一口价数据到历史表
     * 不拷贝装笼宠物
     * @param realm 服务器信息
     * @throws SQLException 数据库异常
     */
    public void copyCheapestAuctionToDaily(Realm realm) throws SQLException {
        String tableName = "cheapest_auction_"
                + TimeUtils.getDate(realm.getLastModified()) + "_"
                + realm.getId();
        checkAndCreateCheapestAuctionDailyTable(tableName);
        runner.update(
                "insert into "
                        + tableName
                        + " (item_id,owner,owner_realm,bid,buyout,quantity,pet_species_id,pet_breed_id,bonus_list,last_modified) select item_id,owner,owner_realm,bid,buyout,quantity,pet_species_id,pet_breed_id,bonus_list,"
                        + System.currentTimeMillis()
                        + " from cheapest_auction where realm_id=? and item_id != 82800",
                realm.getId());
    }

    private void checkAndCreateCheapestAuctionDailyTable(String tableName) throws SQLException {
        if (!DBUtils.isTableExist(tableName)) {
            StringBuffer sb = new StringBuffer();
            sb.append("CREATE TABLE IF NOT EXISTS " + tableName + " (");
            sb.append("id INT UNSIGNED NOT NULL AUTO_INCREMENT,");
            sb.append("item_id INT UNSIGNED NOT NULL,");
            sb.append("owner VARCHAR(12) NOT NULL,");
            sb.append("owner_realm VARCHAR(8) NOT NULL,");
            sb.append("bid BIGINT UNSIGNED NOT NULL,");
            sb.append("buyout BIGINT UNSIGNED NOT NULL,");
            sb.append("quantity INT UNSIGNED NOT NULL,");
            sb.append("pet_species_id INT UNSIGNED NOT NULL,");
            sb.append("pet_breed_id INT UNSIGNED NOT NULL,");
            sb.append("bonus_list VARCHAR(20) NOT NULL,");
            sb.append("last_modified BIGINT UNSIGNED NOT NULL,");
            sb.append("PRIMARY KEY(id)");
            sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            runner.update(sb.toString());
            runner.update("ALTER TABLE " + tableName + " ADD INDEX(item_id)");
            logger.info("表{}未创建， 创建表和索引", tableName);
        }
    }

    /**
     * 查询服务器某天的所有历史数据
     * @return
     */
    public List<CheapestAuctionDaily> findCheapestAuctionDailyByRealmIdAndDate(int realmId, String date) throws SQLException {
        String tableName = "cheapest_auction_"
                + date + "_"
                + realmId;
        return runner.query(
                "select id,item_id as itemId,owner,owner_realm as ownerRealm,bid,buyout,quantity,pet_species_id as petSpeciesId,pet_breed_id as petBreedId,bonus_list as bonusList,last_modified as lastModified from "
                + tableName,
                new BeanListHandler<CheapestAuctionDaily>(CheapestAuctionDaily.class));
    }

    /**
     * 删除某天的拍卖历史数据
     * @param realmId
     * @param date
     * @throws SQLException
     */
    public void dropCheapestAuctionDaily(int realmId, String date) throws SQLException {
        String tableName = "cheapest_auction_"
                + date + "_"
                + realmId;
        if (DBUtils.isTableExist(tableName)) {
            runner.update("drop table " + tableName);
            logger.info("drop table {}", tableName);
        } else {
            logger.info("{} not exist", tableName);
        }
    }

    /**
     * 保存拍卖数据到每月归档表
     * @param aucs
     * @param realmId
     * @param month
     * @throws SQLException
     */
    public void saveCheapestAuctionMonthlies(List<CheapestAuctionMonthly> aucs, int realmId, String month) throws SQLException {
        String tableName = "cheapest_auction_" + month + "_" + realmId;
        checkAndCreateTable(tableName);
        Connection con = DBUtils.getDataSource().getConnection();
        try {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            Object[][] params = new Object[aucs.size()][7];
            for (int i = 0; i < aucs.size(); i++) {
                CheapestAuctionMonthly auc = aucs.get(i);
                params[i][0] = auc.getItemId();
                params[i][1] = auc.getBuyout();
                params[i][2] = auc.getQuantity();
                params[i][3] = auc.getPetSpeciesId();
                params[i][4] = auc.getPetBreedId();
                params[i][5] = auc.getBonusList();
                params[i][6] = auc.getLastModified();
            }
            runner.batch(con, "insert into "
                            + tableName
                            + " (item_id,buyout,quantity,pet_species_id,pet_breed_id,bonus_list,last_modified) values(?,?,?,?,?,?,?)",
                    params);
            con.commit();
            con.setAutoCommit(autoCommit);
        } finally {
            DbUtils.closeQuietly(con);
        }
    }

    private void checkAndCreateTable(String tableName) throws SQLException {
        logger.debug("检查表{}是否存在", tableName);
        if (!DBUtils.isTableExist(tableName)) {
            StringBuffer sb = new StringBuffer();
            sb.append("CREATE TABLE IF NOT EXISTS " + tableName + " (");
            sb.append("id INT UNSIGNED NOT NULL AUTO_INCREMENT,");
            sb.append("item_id INT UNSIGNED NOT NULL,");
            sb.append("buyout BIGINT UNSIGNED NOT NULL,");
            sb.append("quantity INT UNSIGNED NOT NULL,");
            sb.append("pet_species_id INT UNSIGNED NOT NULL,");
            sb.append("pet_breed_id INT UNSIGNED NOT NULL,");
            sb.append("bonus_list VARCHAR(20) NOT NULL,");
            sb.append("last_modified BIGINT UNSIGNED NOT NULL,");
            sb.append("PRIMARY KEY(id)");
            sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            runner.update(sb.toString());
            runner.update("ALTER TABLE " + tableName + " ADD INDEX(item_id)");
            logger.info("表{}未创建， 创建表和索引", tableName);
        }
    }

    /**
     * 获取当前所有服务器拍卖行的物品id
     * @return 物品id，唯一
     * @throws SQLException 数据库异常
     */
    public List<Integer> getItemIds() throws SQLException {
        return runner.query(
                "select distinct item_id from cheapest_auction",
                new ColumnListHandler<Integer>());
    }

    /**
     * 获取拍卖行中所有的item bonus,用来添加新的bonus
     * 1. 宠物(item_id = 82800)不需要
     * 2. 只更新一下类型(class)
     * 武器(2)
     * 护甲(4)
     * 宝石(3) 主要是圣物
     * @return item bonus列表
     */
    public List<ItemBonus> findAllItemBonuses() throws SQLException {
        return runner.query(
                "select a.item_id as itemId,a.bonus_list as bonusList,i.item_class as itemClass,i.level from cheapest_auction a join item i on a.item_id=i.id where i.item_class in (2,3,4) and i.level >= 600 group by item_id,bonus_list",
                new BeanListHandler<ItemBonus>(ItemBonus.class));
    }

    public List<CheapestAuction> findCheapestAuctions(CheapestAuction auction) throws SQLException {
        String sql = "select item_id as itemId,owner,buyout,total_quantity as totalQuantity from cheapest_auction where item_id=? and bonus_list=?";
        return runner.query(
                sql,
                new BeanListHandler<CheapestAuction>(CheapestAuction.class), auction.getItemId(), auction.getBonusList());
    }
}
