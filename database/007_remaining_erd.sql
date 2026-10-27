-- Remaining ERD tables from the team schema. No DROP statements or account data.

-- All user references use CHAR(9) to preserve leading-zero student IDs.

CREATE TABLE IF NOT EXISTS `Administrators` (
  `userID` CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `administrator_since` date NOT NULL,
  PRIMARY KEY (`userID`),
  CONSTRAINT `administrators_ibfk_1` FOREIGN KEY (`userID`) REFERENCES `Users` (`userID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `Course` (
  `courseId` varchar(20) NOT NULL,
  `course_name` varchar(150) NOT NULL,
  PRIMARY KEY (`courseId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `Section` (
  `sectionId` int NOT NULL,
  `courseId` varchar(20) NOT NULL,
  `section_number` varchar(10) NOT NULL,
  `semester` varchar(20) NOT NULL,
  `year` int NOT NULL,
  PRIMARY KEY (`sectionId`),
  KEY `courseId` (`courseId`),
  CONSTRAINT `section_ibfk_1` FOREIGN KEY (`courseId`) REFERENCES `Course` (`courseId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ClassMeeting` (
  `meetingId` int NOT NULL,
  `sectionId` int NOT NULL,
  `classroomId` int NOT NULL,
  `day_of_week` varchar(20) NOT NULL,
  `start_time` time NOT NULL,
  `end_time` time NOT NULL,
  PRIMARY KEY (`meetingId`),
  KEY `sectionId` (`sectionId`),
  KEY `classroomId` (`classroomId`),
  CONSTRAINT `classmeeting_ibfk_1` FOREIGN KEY (`sectionId`) REFERENCES `Section` (`sectionId`),
  CONSTRAINT `classmeeting_ibfk_2` FOREIGN KEY (`classroomId`) REFERENCES `Classrooms` (`classroomId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `Reports` (
  `reportId` int NOT NULL,
  `userID` CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `resolvedByAdminID` CHAR(9) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `classroomId` int DEFAULT NULL,
  `buildingId` int DEFAULT NULL,
  `written_report` text NOT NULL,
  `report_status` varchar(20) NOT NULL,
  PRIMARY KEY (`reportId`),
  KEY `userID` (`userID`),
  KEY `resolvedByAdminID` (`resolvedByAdminID`),
  KEY `fk_reports_classroom` (`classroomId`),
  KEY `fk_reports_building` (`buildingId`),
  CONSTRAINT `fk_reports_building` FOREIGN KEY (`buildingId`) REFERENCES `Building` (`buildingId`),
  CONSTRAINT `fk_reports_classroom` FOREIGN KEY (`classroomId`) REFERENCES `Classrooms` (`classroomId`),
  CONSTRAINT `reports_ibfk_1` FOREIGN KEY (`userID`) REFERENCES `Users` (`userID`),
  CONSTRAINT `reports_ibfk_2` FOREIGN KEY (`resolvedByAdminID`) REFERENCES `Administrators` (`userID`),
  CONSTRAINT `chk_report_target` CHECK ((((`classroomId` is not null) and (`buildingId` is null)) or ((`classroomId` is null) and (`buildingId` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `Suspension` (
  `suspensionID` int NOT NULL,
  `issuedByAdminID` CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `userID` CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reason` varchar(255) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  PRIMARY KEY (`suspensionID`),
  KEY `issuedByAdminID` (`issuedByAdminID`),
  KEY `userID` (`userID`),
  CONSTRAINT `suspension_ibfk_1` FOREIGN KEY (`issuedByAdminID`) REFERENCES `Administrators` (`userID`),
  CONSTRAINT `suspension_ibfk_2` FOREIGN KEY (`userID`) REFERENCES `Users` (`userID`),
  CONSTRAINT `suspension_chk_1` CHECK (((`end_date` is null) or (`end_date` >= `start_date`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
