--
-- V1: baseline of the schema as it existed before Flyway was introduced.
--
-- Generated from the live database with `mysqldump --no-data`. Existing databases
-- (local dev and Railway) are marked as already at V1 via flyway.baseline-on-migrate,
-- so this script does NOT run against them. It exists so a brand-new database can be
-- built from scratch by migrations alone.
--
-- Deliberately does NOT include the refresh-token rotation columns — those arrive in V2,
-- which is what production still needs to apply.
--

--
-- Foreign key checks are disabled for the duration of this script.
--
-- mysqldump emits CREATE TABLE statements in ALPHABETICAL order, so interview_rounds (which
-- references job_applications) is created before it, and job_applications references users,
-- which comes last. Running this against an empty database therefore fails with
-- "Error 1824: Failed to open the referenced table".
--
-- mysqldump normally guards against this itself, but the header carrying SET FOREIGN_KEY_CHECKS=0
-- was stripped by the --compact --skip-comments flags used to generate this file.
--
-- The setting is session-scoped and Flyway runs each migration on a single connection, so it
-- applies to this script alone and cannot leak into the application's own connections.
--
SET FOREIGN_KEY_CHECKS = 0;

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_log` (
  `activity_id` int NOT NULL AUTO_INCREMENT,
  `action` enum('JOB_CREATED','JOB_DELETED','JOB_UPDATED','OFFER_RECEIVED','REJECTED','ROUND_SCHEDULED','STATUS_CHANGED') NOT NULL,
  `company_name` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `job_id` int NOT NULL,
  `job_role` varchar(255) NOT NULL,
  `previous_status` enum('APPLIED','INTERVIEW','OFFER','REJECTED','WISHLIST') DEFAULT NULL,
  `status` enum('APPLIED','INTERVIEW','OFFER','REJECTED','WISHLIST') DEFAULT NULL,
  `user_id` int NOT NULL,
  PRIMARY KEY (`activity_id`),
  KEY `idx_activity_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `interview_rounds` (
  `job_round_id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `feedback` text,
  `interviewer_name` varchar(255) DEFAULT NULL,
  `notes` text,
  `outcome` enum('ACCEPTED','NO_RESPONSE','OTHER','PENDING','REJECTED','WITHDRAWN') DEFAULT NULL,
  `round_date` datetime(6) DEFAULT NULL,
  `round_number` int NOT NULL,
  `round_type` enum('Behavioral','CaseStudy','Coding','CultureFit','Group','HLD','HR','LLD','Managerial','Other','SystemDesign','Technical') NOT NULL,
  `job_id` int NOT NULL,
  PRIMARY KEY (`job_round_id`),
  KEY `FKrxc9x97k28lko6kx3i85dmuup` (`job_id`),
  CONSTRAINT `FKrxc9x97k28lko6kx3i85dmuup` FOREIGN KEY (`job_id`) REFERENCES `job_applications` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_ai_results` (
  `ai_result_id` int NOT NULL AUTO_INCREMENT,
  `ai_cover_letter` text,
  `ai_resume_analysis` text,
  `generated_at` datetime(6) NOT NULL,
  `jd_match_score` double DEFAULT NULL,
  `job_description` text NOT NULL,
  `job_id` int NOT NULL,
  PRIMARY KEY (`ai_result_id`),
  KEY `FKixf3g2fg0s28lbbr0dfho5k07` (`job_id`),
  CONSTRAINT `FKixf3g2fg0s28lbbr0dfho5k07` FOREIGN KEY (`job_id`) REFERENCES `job_applications` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_applications` (
  `job_id` int NOT NULL AUTO_INCREMENT,
  `applied_date` datetime(6) DEFAULT NULL,
  `company_name` varchar(255) NOT NULL,
  `cover_letter_url` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `follow_up_date` datetime(6) DEFAULT NULL,
  `job_role` varchar(255) NOT NULL,
  `job_url` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `priority` enum('HIGH','MEDIUM','LOW') DEFAULT NULL,
  `recruiter_email` varchar(255) DEFAULT NULL,
  `recruiter_name` varchar(255) DEFAULT NULL,
  `recruiter_phone` varchar(255) DEFAULT NULL,
  `reminder_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `resume_url` varchar(255) DEFAULT NULL,
  `salary_range` varchar(255) DEFAULT NULL,
  `status` enum('APPLIED','INTERVIEW','OFFER','REJECTED','WISHLIST') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` int NOT NULL,
  `job_type` enum('HYBRID','ONSITE','REMOTE') DEFAULT NULL,
  `reminder_sent_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`job_id`),
  KEY `FKqs2guhg7p83917vto86imuthy` (`user_id`),
  CONSTRAINT `FKqs2guhg7p83917vto86imuthy` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `password_reset_token_id` int NOT NULL AUTO_INCREMENT,
  `expires_at` datetime(6) NOT NULL,
  `token` varchar(255) NOT NULL,
  `used` tinyint(1) NOT NULL,
  `user_id` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`password_reset_token_id`),
  UNIQUE KEY `UK71lqwbwtklmljk3qlsugr1mig` (`token`),
  KEY `FKk3ndxg5xp6v7wd4gjyusp15gq` (`user_id`),
  CONSTRAINT `FKk3ndxg5xp6v7wd4gjyusp15gq` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `refresh_token_id` int NOT NULL AUTO_INCREMENT,
  `device_info` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked` tinyint(1) NOT NULL,
  `token` varchar(255) NOT NULL,
  `user_id` int NOT NULL,
  PRIMARY KEY (`refresh_token_id`),
  UNIQUE KEY `UKghpmfn23vmxfu3spu3lfg4r2d` (`token`),
  KEY `FK1lih5y2npsf8u5o3vhdb9y0os` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stored_files` (
  `file_id` int NOT NULL AUTO_INCREMENT,
  `content_type` varchar(128) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `public_id` varchar(512) NOT NULL,
  `public_url` varchar(1024) DEFAULT NULL,
  `purpose` enum('AVATAR','COVER_LETTER','RESUME') NOT NULL,
  `resource_type` varchar(32) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `user_id` int NOT NULL,
  PRIMARY KEY (`file_id`),
  KEY `idx_stored_files_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `avatar_url` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `is_active` tinyint(1) NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `provider` enum('GITHUB','GOOGLE','LINKEDIN','LOCAL') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `first_name` varchar(255) NOT NULL,
  `last_name` varchar(255) DEFAULT NULL,
  `default_resume_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

SET FOREIGN_KEY_CHECKS = 1;
