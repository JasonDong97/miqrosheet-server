/*
 Navicat Premium Data Transfer

 Source Server         : 10.36.160.33_3306
 Source Server Type    : MySQL
 Source Server Version : 80404
 Source Host           : 10.36.160.33:3306
 Source Schema         : miqrosheet

 Target Server Type    : MySQL
 Target Server Version : 80404
 File Encoding         : 65001

 Date: 27/10/2025 17:18:34
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for wb
-- ----------------------------
DROP TABLE IF EXISTS `wb`;
CREATE TABLE `wb`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grid_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '唯一标识',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '名称',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UKc72ehjto4o99qeb0j3349thd2`(`grid_key` ASC) USING BTREE,
  INDEX `name`(`name` ASC) USING BTREE,
  INDEX `create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wb_sheet
-- ----------------------------
DROP TABLE IF EXISTS `wb_sheet`;
CREATE TABLE `wb_sheet`  (
  `id` bigint NOT NULL,
  `grid_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci GENERATED ALWAYS AS (json_unquote(json_extract(`json_data`,_utf8mb4'$.name'))) STORED NULL,
  `index` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci GENERATED ALWAYS AS (json_unquote(json_extract(`json_data`,_utf8mb4'$.index'))) STORED NULL,
  `status` tinyint(1) GENERATED ALWAYS AS (json_unquote(json_extract(`json_data`,_utf8mb4'$.status'))) STORED NULL,
  `color` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci GENERATED ALWAYS AS (json_unquote(json_extract(`json_data`,_utf8mb4'$.color'))) STORED NULL,
  `order` int GENERATED ALWAYS AS (json_unquote(json_extract(`json_data`,_utf8mb4'$.order'))) STORED NULL,
  `json_data` json NOT NULL,
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `name`(`name` ASC) USING BTREE,
  INDEX `index`(`index` ASC) USING BTREE,
  INDEX `status`(`status` ASC) USING BTREE,
  INDEX `order`(`order` ASC) USING BTREE,
  INDEX `create_time`(`create_time` ASC) USING BTREE,
  INDEX `grid_key`(`grid_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for wb_sheet_celldata
-- ----------------------------
DROP TABLE IF EXISTS `wb_sheet_celldata`;
CREATE TABLE `wb_sheet_celldata`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grid_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `sheet_index` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `r` int NOT NULL COMMENT '行',
  `c` int NOT NULL COMMENT '列',
  `v` json NOT NULL COMMENT '值',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `sheet_index`(`sheet_index` ASC) USING BTREE,
  INDEX `r`(`r` ASC) USING BTREE,
  INDEX `c`(`c` ASC) USING BTREE,
  INDEX `grid_key`(`grid_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 147 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
