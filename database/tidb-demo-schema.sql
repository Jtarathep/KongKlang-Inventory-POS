-- KONGKLANG: NEW EMPTY TiDB demo database ONLY.
-- Run once. Stop on the first error; do not continue or rerun on a populated database.
-- No DROP, DELETE, TRUNCATE, accounts, passwords or shop data are included.
-- Enable CHECK enforcement before creating tables; do not ignore an error here.
SET GLOBAL tidb_enable_check_constraint = ON;
CREATE DATABASE kongklang_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE kongklang_demo;
SET NAMES utf8mb4;
CREATE TABLE `audit_logs` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `store_id` bigint NOT NULL,

  `user_id` bigint NOT NULL,

  `action` varchar(80) NOT NULL,

  `reference` varchar(255) NOT NULL,

  `created_at` datetime(6) NOT NULL,

  PRIMARY KEY (`id`),

  KEY `ix_audit_store_id` (`store_id`,`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stores` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `active` bit(1) NOT NULL,

  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `created_at` datetime(6) NOT NULL,

  `phone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `store_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `store_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `UKde7abdc4t7p11hmkx9fw4qa4` (`store_code`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `suppliers` (

  `active` bit(1) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `store_id` bigint NOT NULL,

  `address` varchar(1000) DEFAULT NULL,

  `code` varchar(255) NOT NULL,

  `contact_name` varchar(255) DEFAULT NULL,

  `email` varchar(255) DEFAULT NULL,

  `name` varchar(255) NOT NULL,

  `phone` varchar(255) DEFAULT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_supplier_store_code` (`store_id`,`code`),

  CONSTRAINT `FK31osod3me2lknglh15pxr5ilb` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE accounting_periods (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL, month_start DATE NOT NULL,
 status VARCHAR(20) NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_period_store_month(store_id,month_start),
 CONSTRAINT fk_period_store FOREIGN KEY(store_id) REFERENCES stores(id),
 CHECK(status IN ('CLOSED','REOPENED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `app_users` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `active` bit(1) NOT NULL,

  `created_at` datetime(6) NOT NULL,

  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `store_id` bigint NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_app_users_username` (`username`),

  KEY `FKmt6ujcrmp951fc9mrdpwvh5a7` (`store_id`),

  CONSTRAINT `FKmt6ujcrmp951fc9mrdpwvh5a7` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `categories` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `store_id` bigint DEFAULT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_categories_store_name` (`store_id`,`name`),

  CONSTRAINT `FKswh8ov7e46aj6bf053xla2c6b` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `gl_accounts` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `store_id` bigint NOT NULL,

  `code` varchar(255) NOT NULL,

  `name` varchar(255) NOT NULL,

  `type` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_gl_account_store_code` (`store_id`,`code`),

  CONSTRAINT `FKo6da6ai7pmtupk0toqv30c15w` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `gl_journals` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `posted_at` datetime(6) NOT NULL,

  `source_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  `reference` varchar(255) NOT NULL,

  `source_type` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_journal_source` (`store_id`,`source_type`,`source_id`),

  CONSTRAINT `FKqognuog8ywdrrw71q540ibgnc` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `inventory_operations` (

  `quantity_after` int NOT NULL,

  `quantity_before` int NOT NULL,

  `value_after` decimal(19,2) NOT NULL,

  `value_before` decimal(19,2) NOT NULL,

  `created_at` datetime(6) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `performed_by_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  `kind` varchar(20) NOT NULL,

  `number` varchar(50) NOT NULL,

  `request_fingerprint` varchar(64) NOT NULL,

  `request_key` varchar(100) NOT NULL,

  `note` varchar(1000) NOT NULL,

  `product_code` varchar(255) NOT NULL,

  `product_name` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `UK8ad6lnex9jnnwmxnjh8tjfve2` (`store_id`,`request_key`),

  KEY `FKj2vcxkg056ywy1t6qnx0wrhs4` (`performed_by_id`),

  CONSTRAINT `FKj2vcxkg056ywy1t6qnx0wrhs4` FOREIGN KEY (`performed_by_id`) REFERENCES `app_users` (`id`),

  CONSTRAINT `FKrpn3hvco0gkppah55olplkv4b` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `ck_inventory_operation` CHECK (((`kind` in (_utf8mb4'RECEIPT',_utf8mb4'OUT',_utf8mb4'ADJUST',_utf8mb4'OPENING',_utf8mb4'BASELINE')) and (`quantity_before` >= 0) and (`quantity_after` >= 0) and (`value_before` >= 0) and (`value_after` >= 0)))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `legacy_inventory_reviews` (

  `operation_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  `snapshot` longtext NOT NULL,

  PRIMARY KEY (`operation_id`),

  CONSTRAINT `fk_legacy_review_operation` FOREIGN KEY (`operation_id`) REFERENCES `inventory_operations` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `products` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `price` decimal(19,2) DEFAULT NULL,

  `quantity` int DEFAULT NULL,

  `category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `unit` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `cost_price` decimal(19,4) DEFAULT NULL,

  `barcode` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `store_id` bigint DEFAULT NULL,

  `version` bigint NOT NULL DEFAULT '0',

  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `active` bit(1) NOT NULL DEFAULT b'1',

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_products_store_code` (`store_id`,`code`),

  UNIQUE KEY `uk_products_store_barcode` (`store_id`,`barcode`),

  CONSTRAINT `FKgcyffheofvmy2x5l78xam63mc` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `purchase_orders` (

  `total` decimal(19,2) NOT NULL,

  `created_at` datetime(6) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `ordered_by_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  `supplier_id` bigint NOT NULL,

  `request_fingerprint` varchar(64) NOT NULL,

  `request_key` varchar(100) NOT NULL,

  `note` varchar(1000) DEFAULT NULL,

  `number` varchar(255) NOT NULL,

  `status` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_po_store_key` (`store_id`,`request_key`),

  UNIQUE KEY `UKhgpv5t6xncmltpegcgo62y54h` (`number`),

  KEY `FKslbdoev2buvl93ul535njrdo6` (`ordered_by_id`),

  KEY `FKrpdasmb8y8xs5tiy4369xpinq` (`supplier_id`),

  CONSTRAINT `FKrkk3qf9gckmx5w6pwm1mmdefp` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `FKrpdasmb8y8xs5tiy4369xpinq` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),

  CONSTRAINT `FKslbdoev2buvl93ul535njrdo6` FOREIGN KEY (`ordered_by_id`) REFERENCES `app_users` (`id`),

  CONSTRAINT `ck_purchase_orders_amount` CHECK ((`total` >= 0))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sales` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `created_at` datetime(6) NOT NULL,

  `sale_number` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `total_amount` decimal(19,2) NOT NULL,

  `store_id` bigint DEFAULT NULL,

  `request_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `request_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `sold_by_id` bigint DEFAULT NULL,

  `payment_method` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `payment_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `total_cost` decimal(19,2) DEFAULT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `UKn0mb9m597xrmi5jbiogh74174` (`sale_number`),

  UNIQUE KEY `uk_sale_store_request` (`store_id`,`request_key`),

  KEY `FKo2i450qq9qv6es5i7l8e397c5` (`store_id`),

  KEY `fk_sale_seller` (`sold_by_id`),

  CONSTRAINT `fk_sale_seller` FOREIGN KEY (`sold_by_id`) REFERENCES `app_users` (`id`),

  CONSTRAINT `FKo2i450qq9qv6es5i7l8e397c5` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_batches` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `expiry_date` date DEFAULT NULL,

  `note` varchar(1200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `product_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `product_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `purchase_cost` decimal(19,4) NOT NULL,

  `quantity` int NOT NULL,

  `received_at` datetime(6) NOT NULL,

  `remaining_quantity` int NOT NULL,

  `store_id` bigint DEFAULT NULL,

  `received_value` decimal(19,2) NOT NULL,

  `remaining_value` decimal(19,2) NOT NULL,

  PRIMARY KEY (`id`),

  KEY `FKcx1y10enqeo029mwyl9w5ncst` (`store_id`),

  CONSTRAINT `FKcx1y10enqeo029mwyl9w5ncst` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `ck_stock_batch_values` CHECK (((`quantity` > 0) and (`remaining_quantity` >= 0) and (`remaining_quantity` <= `quantity`) and (`received_value` >= 0) and (`remaining_value` >= 0) and (`remaining_value` <= `received_value`)))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stock_transactions` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `balance` int DEFAULT NULL,

  `created_at` datetime(6) DEFAULT NULL,

  `note` varchar(1200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `product_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `product_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `quantity` int DEFAULT NULL,

  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,

  `store_id` bigint DEFAULT NULL,

  PRIMARY KEY (`id`),

  KEY `FKgg1yg88q3yaov69dnlixhdi4u` (`store_id`),

  CONSTRAINT `FKgg1yg88q3yaov69dnlixhdi4u` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sale_tenders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 sale_id BIGINT NOT NULL,
 method VARCHAR(20) NOT NULL,
 amount DECIMAL(19,2) NOT NULL,
 received DECIMAL(19,2) NOT NULL,
 change_amount DECIMAL(19,2) NOT NULL,
 reference VARCHAR(100) NOT NULL,
 CONSTRAINT uk_sale_tender_method UNIQUE(sale_id,method),
 CONSTRAINT fk_sale_tender_sale FOREIGN KEY(sale_id) REFERENCES sales(id),
 CONSTRAINT ck_sale_tender CHECK(method IN ('CASH','TRANSFER','CARD') AND amount>0 AND received>=amount AND change_amount=received-amount AND (method='CASH' OR change_amount=0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_returns (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 store_id BIGINT NOT NULL, sale_id BIGINT NOT NULL, performed_by_id BIGINT NOT NULL,
 request_key VARCHAR(100) NOT NULL, fingerprint VARCHAR(64) NOT NULL, number VARCHAR(50) NOT NULL,
 reason VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL,
 net_amount DECIMAL(19,2) NOT NULL, tax_amount DECIMAL(19,2) NOT NULL, total_amount DECIMAL(19,2) NOT NULL,
 restocked_cost DECIMAL(19,2) NOT NULL,
 UNIQUE KEY uk_return_request(store_id,request_key), UNIQUE KEY uk_return_number(number),
 CONSTRAINT fk_return_store FOREIGN KEY(store_id) REFERENCES stores(id),
 CONSTRAINT fk_return_sale FOREIGN KEY(sale_id) REFERENCES sales(id),
 CONSTRAINT fk_return_user FOREIGN KEY(performed_by_id) REFERENCES app_users(id),
 CHECK(net_amount>=0 AND tax_amount>=0 AND total_amount=net_amount+tax_amount AND restocked_cost>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_refund_tenders (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, return_id BIGINT NOT NULL,
 method VARCHAR(20) NOT NULL, amount DECIMAL(19,2) NOT NULL, reference VARCHAR(100) NOT NULL,
 UNIQUE KEY uk_refund_method(return_id,method),
 CONSTRAINT fk_refund_header FOREIGN KEY(return_id) REFERENCES sale_returns(id),
 CHECK(method IN ('CASH','TRANSFER','CARD') AND amount>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounting_period_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, period_id BIGINT NOT NULL, performed_by_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL, reason VARCHAR(1000) NOT NULL, created_at DATETIME(6) NOT NULL,
 journal_id BIGINT,
 CONSTRAINT fk_period_event_period FOREIGN KEY(period_id) REFERENCES accounting_periods(id),
 CONSTRAINT fk_period_event_user FOREIGN KEY(performed_by_id) REFERENCES app_users(id),
 CONSTRAINT fk_period_event_journal FOREIGN KEY(journal_id) REFERENCES gl_journals(id),
 CHECK(kind IN ('CLOSE','REOPEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounting_period_balances (
 event_id BIGINT NOT NULL, account_id BIGINT NOT NULL, debit DECIMAL(19,2) NOT NULL, credit DECIMAL(19,2) NOT NULL,
 PRIMARY KEY(event_id,account_id),
 CONSTRAINT fk_period_balance_event FOREIGN KEY(event_id) REFERENCES accounting_period_events(id),
 CONSTRAINT fk_period_balance_account FOREIGN KEY(account_id) REFERENCES gl_accounts(id),
 CHECK(debit>=0 AND credit>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE product_photos (
 product_id BIGINT NOT NULL PRIMARY KEY,
 store_id BIGINT NOT NULL,
 image_data MEDIUMBLOB NOT NULL,
 CONSTRAINT fk_product_photo_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
 CONSTRAINT fk_product_photo_store FOREIGN KEY (store_id) REFERENCES stores(id),
 INDEX ix_product_photos_store (store_id)
);

CREATE TABLE `gl_journal_lines` (

  `credit` decimal(19,2) NOT NULL,

  `debit` decimal(19,2) NOT NULL,

  `account_id` bigint NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `journal_id` bigint NOT NULL,

  PRIMARY KEY (`id`),

  KEY `FKqy7etsf2tgjqdwfpswbljl4i1` (`account_id`),

  KEY `FK3kgaq0t0vdsihble70wk1xe9n` (`journal_id`),

  CONSTRAINT `FK3kgaq0t0vdsihble70wk1xe9n` FOREIGN KEY (`journal_id`) REFERENCES `gl_journals` (`id`),

  CONSTRAINT `FKqy7etsf2tgjqdwfpswbljl4i1` FOREIGN KEY (`account_id`) REFERENCES `gl_accounts` (`id`),

  CONSTRAINT `ck_gl_journal_lines_amount` CHECK (((`debit` >= 0) and (`credit` >= 0) and (((`debit` > 0) and (`credit` = 0)) or ((`credit` > 0) and (`debit` = 0)))))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `goods_receipts` (

  `total` decimal(19,2) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `purchase_order_id` bigint NOT NULL,

  `received_at` datetime(6) NOT NULL,

  `received_by_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  `request_fingerprint` varchar(64) NOT NULL,

  `request_key` varchar(100) NOT NULL,

  `number` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_receipt_store_key` (`store_id`,`request_key`),

  UNIQUE KEY `UK2ogsw29clmikxf3ah852snqt1` (`number`),

  KEY `FK31kyyaqb354qfc4pssihmmry5` (`purchase_order_id`),

  KEY `FKo6uo94hwnk3t6yledvm0pi2sv` (`received_by_id`),

  CONSTRAINT `FK31kyyaqb354qfc4pssihmmry5` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`),

  CONSTRAINT `FKaf0s3qvdaiqrd082w43hs9ioo` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `FKo6uo94hwnk3t6yledvm0pi2sv` FOREIGN KEY (`received_by_id`) REFERENCES `app_users` (`id`),

  CONSTRAINT `ck_goods_receipts_amount` CHECK ((`total` >= 0))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `inventory_operation_items` (

  `quantity_change` int NOT NULL,

  `unit_cost` decimal(19,4) NOT NULL,

  `value_change` decimal(19,2) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `operation_id` bigint NOT NULL,

  `stock_batch_id` bigint NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `UKp7hvw5jb6kwliiybymy2rw9pm` (`operation_id`,`stock_batch_id`),

  KEY `FKleb84lafsbv3flawu1wytg8qd` (`stock_batch_id`),

  CONSTRAINT `FK5nafvoqacrjipho61uwhjqx4e` FOREIGN KEY (`operation_id`) REFERENCES `inventory_operations` (`id`),

  CONSTRAINT `FKleb84lafsbv3flawu1wytg8qd` FOREIGN KEY (`stock_batch_id`) REFERENCES `stock_batches` (`id`),

  CONSTRAINT `ck_inventory_operation_item` CHECK (((`unit_cost` >= 0) and (((`quantity_change` > 0) and (`value_change` >= 0)) or ((`quantity_change` < 0) and (`value_change` <= 0)))))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `purchase_order_items` (

  `quantity` int NOT NULL,

  `received_quantity` int NOT NULL,

  `unit_cost` decimal(19,4) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `product_id` bigint NOT NULL,

  `purchase_order_id` bigint NOT NULL,

  `product_code` varchar(255) NOT NULL,

  `product_name` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  KEY `FKs16e5vrvsp8alu0xp8m3a2ol5` (`product_id`),

  KEY `FKo3yj8ocbw2kav38548t22hgh8` (`purchase_order_id`),

  CONSTRAINT `FKo3yj8ocbw2kav38548t22hgh8` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`),

  CONSTRAINT `FKs16e5vrvsp8alu0xp8m3a2ol5` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),

  CONSTRAINT `ck_purchase_order_items_amount` CHECK (((`quantity` > 0) and (`received_quantity` >= 0) and (`received_quantity` <= `quantity`) and (`unit_cost` >= 0)))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sale_items` (

  `id` bigint NOT NULL AUTO_INCREMENT,

  `price` decimal(19,2) NOT NULL,

  `product_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `product_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,

  `quantity` int NOT NULL,

  `subtotal` decimal(19,2) NOT NULL,

  `sale_id` bigint NOT NULL,

  `cost` decimal(19,2) NOT NULL,

  `actual_cost` decimal(19,4) DEFAULT NULL,

  PRIMARY KEY (`id`),

  KEY `FK7tcpbc5c5mpnm8fl2phl8ep7l` (`sale_id`),

  CONSTRAINT `FK7tcpbc5c5mpnm8fl2phl8ep7l` FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sale_return_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, return_id BIGINT NOT NULL, sale_item_id BIGINT NOT NULL,
 quantity INT NOT NULL, restock BIT(1) NOT NULL,
 net_amount DECIMAL(19,2) NOT NULL, tax_amount DECIMAL(19,2) NOT NULL, total_amount DECIMAL(19,2) NOT NULL,
 financial_cost DECIMAL(19,2) NOT NULL,
 UNIQUE KEY uk_return_item(return_id,sale_item_id),
 CONSTRAINT fk_return_item_header FOREIGN KEY(return_id) REFERENCES sale_returns(id),
 CONSTRAINT fk_return_item_sale FOREIGN KEY(sale_item_id) REFERENCES sale_items(id),
 CHECK(quantity>0 AND net_amount>=0 AND tax_amount>=0 AND total_amount=net_amount+tax_amount AND financial_cost>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `ap_invoices` (

  `amount` decimal(19,2) NOT NULL,

  `due_date` date NOT NULL,

  `invoice_date` date NOT NULL,

  `paid` decimal(19,2) NOT NULL,

  `created_at` datetime(6) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `receipt_id` bigint DEFAULT NULL,

  `store_id` bigint NOT NULL,

  `supplier_id` bigint NOT NULL,

  `vendor_number` varchar(100) NOT NULL,

  `status` varchar(255) NOT NULL,

  `inventory_operation_id` bigint DEFAULT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_invoice_vendor` (`store_id`,`supplier_id`,`vendor_number`),

  UNIQUE KEY `uk_invoice_receipt` (`receipt_id`),

  UNIQUE KEY `uk_invoice_inventory_operation` (`inventory_operation_id`),

  KEY `FKq9n6rpbuww644mmsmrntonrbq` (`supplier_id`),

  CONSTRAINT `fk_invoice_inventory_operation` FOREIGN KEY (`inventory_operation_id`) REFERENCES `inventory_operations` (`id`),

  CONSTRAINT `FKd1ru33p55ffrwfhikygf0oped` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `FKe2w9vq6xavett5je3jxqecsj0` FOREIGN KEY (`receipt_id`) REFERENCES `goods_receipts` (`id`),

  CONSTRAINT `FKq9n6rpbuww644mmsmrntonrbq` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),

  CONSTRAINT `ck_ap_invoices_amount` CHECK (((`amount` >= 0) and (`paid` >= 0) and (`paid` <= `amount`))),

  CONSTRAINT `ck_invoice_one_source` CHECK ((((`receipt_id` is not null) and (`inventory_operation_id` is null)) or ((`receipt_id` is null) and (`inventory_operation_id` is not null))))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ap_payments` (

  `amount` decimal(19,2) NOT NULL,

  `payment_date` date NOT NULL,

  `created_at` datetime(6) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `invoice_id` bigint NOT NULL,

  `paid_by_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  `request_key` varchar(100) NOT NULL,

  `note` varchar(1000) DEFAULT NULL,

  `number` varchar(255) NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_payment_store_key` (`store_id`,`request_key`),

  UNIQUE KEY `UK43q6fw6upc48mbak104j94qs5` (`number`),

  KEY `FK3iueq8yp9tvx6mr3iuec8w18h` (`invoice_id`),

  KEY `FK3ylyyr5pesp335mnpvcv76p3b` (`paid_by_id`),

  CONSTRAINT `FK3iueq8yp9tvx6mr3iuec8w18h` FOREIGN KEY (`invoice_id`) REFERENCES `ap_invoices` (`id`),

  CONSTRAINT `FK3ylyyr5pesp335mnpvcv76p3b` FOREIGN KEY (`paid_by_id`) REFERENCES `app_users` (`id`),

  CONSTRAINT `FKl58fja69r4epqciqb78pfd2xx` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `ck_ap_payments_amount` CHECK ((`amount` > 0))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `goods_receipt_items` (

  `expiry_date` date DEFAULT NULL,

  `quantity` int NOT NULL,

  `unit_cost` decimal(19,4) NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `order_item_id` bigint NOT NULL,

  `receipt_id` bigint NOT NULL,

  `stock_batch_id` bigint NOT NULL,

  PRIMARY KEY (`id`),

  KEY `FK73detaex7luvladjdpv8hfh44` (`order_item_id`),

  KEY `FKn54w1n5pkl0iuvv6jwmd4qfxl` (`receipt_id`),

  KEY `FKp4exf8ndgb3vp6kgo4r4o6g2s` (`stock_batch_id`),

  CONSTRAINT `FK73detaex7luvladjdpv8hfh44` FOREIGN KEY (`order_item_id`) REFERENCES `purchase_order_items` (`id`),

  CONSTRAINT `FKn54w1n5pkl0iuvv6jwmd4qfxl` FOREIGN KEY (`receipt_id`) REFERENCES `goods_receipts` (`id`),

  CONSTRAINT `FKp4exf8ndgb3vp6kgo4r4o6g2s` FOREIGN KEY (`stock_batch_id`) REFERENCES `stock_batches` (`id`),

  CONSTRAINT `ck_goods_receipt_items_amount` CHECK (((`quantity` > 0) and (`unit_cost` >= 0)))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sale_allocations` (

  `actual_cost` decimal(19,4) NOT NULL,

  `financial_cost` decimal(19,2) NOT NULL,

  `quantity` int NOT NULL,

  `unit_cost` decimal(19,4) NOT NULL,

  `batch_id` bigint NOT NULL,

  `id` bigint NOT NULL AUTO_INCREMENT,

  `sale_item_id` bigint NOT NULL,

  `store_id` bigint NOT NULL,

  PRIMARY KEY (`id`),

  UNIQUE KEY `uk_sale_item_batch` (`sale_item_id`,`batch_id`),

  KEY `FKdqgpeih01nci7kdldtvuqa8nx` (`batch_id`),

  KEY `FKh4b0px8w1tkgydlbcffpgt6jp` (`store_id`),

  CONSTRAINT `FKdqgpeih01nci7kdldtvuqa8nx` FOREIGN KEY (`batch_id`) REFERENCES `stock_batches` (`id`),

  CONSTRAINT `FKh4b0px8w1tkgydlbcffpgt6jp` FOREIGN KEY (`store_id`) REFERENCES `stores` (`id`),

  CONSTRAINT `FKso477osntfvk4c9vao33hi2gq` FOREIGN KEY (`sale_item_id`) REFERENCES `sale_items` (`id`),

  CONSTRAINT `ck_sale_allocation_amount` CHECK (((`quantity` > 0) and (`unit_cost` >= 0) and (`actual_cost` >= 0) and (`financial_cost` >= 0) and (`actual_cost` = (`unit_cost` * `quantity`))))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sale_return_allocations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, return_item_id BIGINT NOT NULL, sale_allocation_id BIGINT NOT NULL,
 quantity INT NOT NULL, financial_cost DECIMAL(19,2) NOT NULL, returned_batch_id BIGINT,
 UNIQUE KEY uk_return_allocation(return_item_id,sale_allocation_id),
 CONSTRAINT fk_return_allocation_item FOREIGN KEY(return_item_id) REFERENCES sale_return_items(id),
 CONSTRAINT fk_return_allocation_source FOREIGN KEY(sale_allocation_id) REFERENCES sale_allocations(id),
 CONSTRAINT fk_return_allocation_batch FOREIGN KEY(returned_batch_id) REFERENCES stock_batches(id),
 CHECK(quantity>0 AND financial_cost>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE stores
 ADD COLUMN tax_mode VARCHAR(20) NOT NULL DEFAULT 'NONE',
 ADD COLUMN vat_rate DECIMAL(5,2) NOT NULL DEFAULT 7.00,
 ADD COLUMN vat_registered BIT(1) NOT NULL DEFAULT 0,
 ADD COLUMN tax_revision BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN legal_name VARCHAR(255),
 ADD COLUMN legal_address VARCHAR(1000),
 ADD COLUMN tax_id VARCHAR(13),
 ADD COLUMN branch_code VARCHAR(5);

ALTER TABLE stores ADD CONSTRAINT ck_store_tax CHECK(tax_mode IN ('NONE','INCLUSIVE','EXCLUSIVE') AND vat_rate BETWEEN 0 AND 100 AND (tax_mode='NONE' OR vat_registered=1));

ALTER TABLE sales
 ADD COLUMN listed_amount DECIMAL(19,2), ADD COLUMN discount_amount DECIMAL(19,2),
 ADD COLUMN net_amount DECIMAL(19,2), ADD COLUMN tax_amount DECIMAL(19,2),
 ADD COLUMN tax_rate DECIMAL(5,2), ADD COLUMN tax_mode VARCHAR(20),
 ADD COLUMN discount_type VARCHAR(20), ADD COLUMN discount_value DECIMAL(19,2),
 ADD COLUMN document_type VARCHAR(20),
 ADD COLUMN seller_name VARCHAR(255), ADD COLUMN seller_address VARCHAR(1000),
 ADD COLUMN seller_tax_id VARCHAR(13), ADD COLUMN seller_branch VARCHAR(5),
 ADD COLUMN buyer_name VARCHAR(255), ADD COLUMN buyer_address VARCHAR(1000),
 ADD COLUMN buyer_tax_id VARCHAR(13), ADD COLUMN buyer_branch VARCHAR(5);

ALTER TABLE sale_items
 ADD COLUMN listed_amount DECIMAL(19,2), ADD COLUMN discount_amount DECIMAL(19,2),
 ADD COLUMN net_amount DECIMAL(19,2), ADD COLUMN tax_amount DECIMAL(19,2);

ALTER TABLE app_users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN password_change_required BIT(1) NOT NULL DEFAULT 0;
