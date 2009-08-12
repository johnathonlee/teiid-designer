/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.connector.jdbc.mysql;

import java.util.ArrayList;
import java.util.List;

import org.teiid.connector.api.SourceSystemFunctions;
import org.teiid.connector.jdbc.JDBCCapabilities;



/** 
 * @since 4.3
 */
public class MySQLCapabilities extends JDBCCapabilities {

    public List<String> getSupportedFunctions() {
        List<String> supportedFunctions = new ArrayList<String>();
        supportedFunctions.addAll(super.getSupportedFunctions());

        supportedFunctions.add(SourceSystemFunctions.ABS); 
        supportedFunctions.add(SourceSystemFunctions.ACOS); 
        supportedFunctions.add(SourceSystemFunctions.ASIN);
        supportedFunctions.add(SourceSystemFunctions.ATAN);
        supportedFunctions.add(SourceSystemFunctions.ATAN2);
        supportedFunctions.add(SourceSystemFunctions.BITAND);
        supportedFunctions.add(SourceSystemFunctions.BITNOT);
        supportedFunctions.add(SourceSystemFunctions.BITOR);
        supportedFunctions.add(SourceSystemFunctions.BITXOR);
        supportedFunctions.add(SourceSystemFunctions.CEILING);
        supportedFunctions.add(SourceSystemFunctions.COS);
        supportedFunctions.add(SourceSystemFunctions.COT);
        supportedFunctions.add(SourceSystemFunctions.DEGREES);
        supportedFunctions.add(SourceSystemFunctions.EXP);
        supportedFunctions.add(SourceSystemFunctions.FLOOR);
        supportedFunctions.add(SourceSystemFunctions.LOG);
        supportedFunctions.add(SourceSystemFunctions.LOG10);
        supportedFunctions.add(SourceSystemFunctions.MOD);
        supportedFunctions.add(SourceSystemFunctions.PI);
        supportedFunctions.add(SourceSystemFunctions.POWER);
        supportedFunctions.add(SourceSystemFunctions.RADIANS);
        supportedFunctions.add(SourceSystemFunctions.ROUND);
        supportedFunctions.add(SourceSystemFunctions.SIGN);
        supportedFunctions.add(SourceSystemFunctions.SIN);
        supportedFunctions.add(SourceSystemFunctions.SQRT);
        supportedFunctions.add(SourceSystemFunctions.TAN);

        supportedFunctions.add(SourceSystemFunctions.ASCII);
        supportedFunctions.add(SourceSystemFunctions.CHAR);
        supportedFunctions.add(SourceSystemFunctions.CONCAT);
        supportedFunctions.add(SourceSystemFunctions.INSERT);
        supportedFunctions.add(SourceSystemFunctions.LCASE);
        supportedFunctions.add(SourceSystemFunctions.LEFT);
        supportedFunctions.add(SourceSystemFunctions.LENGTH);
        supportedFunctions.add(SourceSystemFunctions.LOCATE);
        supportedFunctions.add(SourceSystemFunctions.LPAD);
        supportedFunctions.add(SourceSystemFunctions.LTRIM);
        supportedFunctions.add(SourceSystemFunctions.REPEAT);
        supportedFunctions.add(SourceSystemFunctions.REPLACE);
        supportedFunctions.add(SourceSystemFunctions.RIGHT);
        supportedFunctions.add(SourceSystemFunctions.RPAD);
        supportedFunctions.add(SourceSystemFunctions.RTRIM);
        supportedFunctions.add(SourceSystemFunctions.SUBSTRING);
        supportedFunctions.add(SourceSystemFunctions.UCASE);
        
        // These are executed within the server and never pushed down
//        supportedFunctions.add("CURDATE"); //$NON-NLS-1$
//        supportedFunctions.add("CURTIME"); //$NON-NLS-1$
//        supportedFunctions.add("NOW"); //$NON-NLS-1$
        supportedFunctions.add(SourceSystemFunctions.DAYNAME);
        supportedFunctions.add(SourceSystemFunctions.DAYOFMONTH);
        supportedFunctions.add(SourceSystemFunctions.DAYOFWEEK);
        supportedFunctions.add(SourceSystemFunctions.DAYOFYEAR);
        
        // These should not be pushed down since the grammar for string conversion is different
//        supportedFunctions.add("FORMATDATE"); //$NON-NLS-1$
//        supportedFunctions.add("FORMATTIME"); //$NON-NLS-1$
//        supportedFunctions.add("FORMATTIMESTAMP"); //$NON-NLS-1$
        supportedFunctions.add(SourceSystemFunctions.HOUR);
        supportedFunctions.add(SourceSystemFunctions.MINUTE);
        supportedFunctions.add(SourceSystemFunctions.MONTH);
        supportedFunctions.add(SourceSystemFunctions.MONTHNAME);
        
        // These should not be pushed down since the grammar for string conversion is different
//        supportedFunctions.add("PARSEDATE"); //$NON-NLS-1$
//        supportedFunctions.add("PARSETIME"); //$NON-NLS-1$
//        supportedFunctions.add("PARSETIMESTAMP"); //$NON-NLS-1$
        supportedFunctions.add(SourceSystemFunctions.QUARTER);
        supportedFunctions.add(SourceSystemFunctions.SECOND);
//        supportedFunctions.add(SourceSystemFunctions.TIMESTAMPADD);
//        supportedFunctions.add(SourceSystemFunctions.TIMESTAMPDIFF);
        supportedFunctions.add(SourceSystemFunctions.WEEK);
        supportedFunctions.add(SourceSystemFunctions.YEAR);

        supportedFunctions.add(SourceSystemFunctions.CONVERT);
        supportedFunctions.add(SourceSystemFunctions.IFNULL);
        supportedFunctions.add(SourceSystemFunctions.COALESCE);
        
//        supportedFunctions.add("GREATEST"); //$NON-NLS-1$
//        supportedFunctions.add("ISNULL"); //$NON-NLS-1$
//        supportedFunctions.add("LEAST"); //$NON-NLS-1$
//        supportedFunctions.add("STRCMP"); // String-specific //$NON-NLS-1$
//        
//        // String
//        supportedFunctions.add("BIN"); //$NON-NLS-1$
//        supportedFunctions.add("BIT_LENGTH"); //$NON-NLS-1$
//        supportedFunctions.add("CHAR_LENGTH"); //$NON-NLS-1$
//        supportedFunctions.add("CHARACTER_LENGTH"); //$NON-NLS-1$
//        supportedFunctions.add("COMPRESS"); //$NON-NLS-1$
//        supportedFunctions.add("CONCAT_WS"); //$NON-NLS-1$
//        supportedFunctions.add("CONV"); //$NON-NLS-1$
//        supportedFunctions.add("ELT"); //$NON-NLS-1$
//        supportedFunctions.add("EXPORT_SET"); //$NON-NLS-1$
//        supportedFunctions.add("FIELD"); //$NON-NLS-1$
//        supportedFunctions.add("FIND_IN_SET"); //$NON-NLS-1$
//        supportedFunctions.add("FORMAT"); //$NON-NLS-1$
//        supportedFunctions.add("HEX"); //$NON-NLS-1$
//        supportedFunctions.add("INSTR"); //$NON-NLS-1$
//        supportedFunctions.add("LOAD_FILE"); //$NON-NLS-1$
//        supportedFunctions.add("MAKE_SET"); //$NON-NLS-1$
//        supportedFunctions.add("MID"); //$NON-NLS-1$
//        supportedFunctions.add("OCT"); //$NON-NLS-1$
//        supportedFunctions.add("OCTET_LENGTH"); //$NON-NLS-1$
//        supportedFunctions.add("ORD"); //$NON-NLS-1$
//        supportedFunctions.add("QUOTE"); //$NON-NLS-1$
//        supportedFunctions.add("REVERSE"); //$NON-NLS-1$
//        supportedFunctions.add("SOUNDEX"); //$NON-NLS-1$
//        supportedFunctions.add("SPACE"); //$NON-NLS-1$
//        supportedFunctions.add("SUBSTR"); //$NON-NLS-1$
//        supportedFunctions.add("SUBSTRING_INDEX"); //$NON-NLS-1$
//        supportedFunctions.add("TRIM"); //$NON-NLS-1$
//        supportedFunctions.add("UNCOMPRESS"); //$NON-NLS-1$
//        supportedFunctions.add("UNHEX"); //$NON-NLS-1$
//        
//        // Math
//        supportedFunctions.add("CEIL"); //$NON-NLS-1$
//        supportedFunctions.add("CRC32"); //$NON-NLS-1$
//          // DIV is an operator equivalent to '/'
//        supportedFunctions.add("DIV"); //$NON-NLS-1$
//        supportedFunctions.add("FORMAT"); //$NON-NLS-1$
//        supportedFunctions.add("LN"); //$NON-NLS-1$
//        supportedFunctions.add("LOG2"); //$NON-NLS-1$
//        supportedFunctions.add("POW"); //$NON-NLS-1$
//        supportedFunctions.add("RAND"); //$NON-NLS-1$
//        supportedFunctions.add("TRUNCATE"); //$NON-NLS-1$
//        
//        // Date / Time
//        supportedFunctions.add("ADDDATE"); //$NON-NLS-1$
//        supportedFunctions.add("ADDTIME"); //$NON-NLS-1$
//        supportedFunctions.add("CONVERT_TZ"); //$NON-NLS-1$
//        supportedFunctions.add("CURRENT_DATE"); //$NON-NLS-1$
//        supportedFunctions.add("CURRENT_TIME"); //$NON-NLS-1$
//        supportedFunctions.add("CURRENT_TIMESTAMP"); //$NON-NLS-1$
//        supportedFunctions.add("DATE"); //$NON-NLS-1$
//        supportedFunctions.add("DATEDIFF"); //$NON-NLS-1$
////        supportedFunctions.add("DATE_ADD");
////        supportedFunctions.add("DATE_SUB");
//        supportedFunctions.add("DATE_FORMAT"); //$NON-NLS-1$
//        supportedFunctions.add("DAY"); //$NON-NLS-1$
////        supportedFunctions.add("EXTRACT");
//        supportedFunctions.add("FROM_DAYS"); //$NON-NLS-1$
//        supportedFunctions.add("FROM_UNIXTIME"); //$NON-NLS-1$
//        supportedFunctions.add("GET_FORMAT"); //$NON-NLS-1$
//        supportedFunctions.add("LAST_DAY"); //$NON-NLS-1$
//        supportedFunctions.add("LOCALTIME"); //$NON-NLS-1$
//        supportedFunctions.add("LOCALTIMESTAMP"); //$NON-NLS-1$
//        supportedFunctions.add("MAKEDATE"); //$NON-NLS-1$
//        supportedFunctions.add("MAKETIME"); //$NON-NLS-1$
//        supportedFunctions.add("MICROSECOND"); //$NON-NLS-1$
//        supportedFunctions.add("PERIOD_ADD"); //$NON-NLS-1$
//        supportedFunctions.add("PERIOD_DIFF"); //$NON-NLS-1$
//        supportedFunctions.add("SEC_TO_TIME"); //$NON-NLS-1$
//        supportedFunctions.add("STR_TO_DATE"); //$NON-NLS-1$
//        supportedFunctions.add("SUBDATE"); //$NON-NLS-1$
//        supportedFunctions.add("SUBTIME"); //$NON-NLS-1$
//        supportedFunctions.add("SYSDATE"); //$NON-NLS-1$
//        supportedFunctions.add("TIME"); //$NON-NLS-1$
//        supportedFunctions.add("TIMEDIFF"); //$NON-NLS-1$
//        supportedFunctions.add("TIMESTAMP"); //$NON-NLS-1$
//        supportedFunctions.add("TIME_FORMAT"); //$NON-NLS-1$
//        supportedFunctions.add("TIME_TO_SEC"); //$NON-NLS-1$
//        supportedFunctions.add("TO_DAYS"); //$NON-NLS-1$
//        supportedFunctions.add("UNIX_TIMESTAMP"); //$NON-NLS-1$
//        supportedFunctions.add("UTC_DATE"); //$NON-NLS-1$
//        supportedFunctions.add("UTC_TIME"); //$NON-NLS-1$
//        supportedFunctions.add("UTC_TIMESTAMP"); //$NON-NLS-1$
//        supportedFunctions.add("WEEKDAY"); //$NON-NLS-1$
//        supportedFunctions.add("WEEKOFYEAR"); //$NON-NLS-1$
//        supportedFunctions.add("YEARWEEK"); //$NON-NLS-1$
//        
//        // Bit
//        supportedFunctions.add("|"); //$NON-NLS-1$
//        supportedFunctions.add("&"); //$NON-NLS-1$
//        supportedFunctions.add("^"); //$NON-NLS-1$
//        supportedFunctions.add("<<"); //$NON-NLS-1$
//        supportedFunctions.add(">>"); //$NON-NLS-1$
//        supportedFunctions.add("~"); //$NON-NLS-1$
//        supportedFunctions.add("BIT_COUNT"); //$NON-NLS-1$
//        
//        // Encryption
//        supportedFunctions.add("AES_ENCRYPT"); //$NON-NLS-1$
//        supportedFunctions.add("AES_DECRYPT"); //$NON-NLS-1$
//        supportedFunctions.add("DECODE"); //$NON-NLS-1$
//        supportedFunctions.add("ENCODE"); //$NON-NLS-1$
//        supportedFunctions.add("DES_ENCRYPT"); //$NON-NLS-1$
//        supportedFunctions.add("DES_DECRYPT"); //$NON-NLS-1$
//        supportedFunctions.add("MD5"); //$NON-NLS-1$
//        supportedFunctions.add("OLD_PASSWORD"); //$NON-NLS-1$
//        supportedFunctions.add("PASSWORD"); //$NON-NLS-1$
//        supportedFunctions.add("SHA"); //$NON-NLS-1$
//        supportedFunctions.add("SHA1"); //$NON-NLS-1$
//        
//        // Information
//        supportedFunctions.add("BENCHMARK"); //$NON-NLS-1$
//        supportedFunctions.add("CHARSET"); //$NON-NLS-1$
//        supportedFunctions.add("COERCIBILITY"); //$NON-NLS-1$
//        supportedFunctions.add("COLLATION"); //$NON-NLS-1$
//        supportedFunctions.add("CONNECTION_ID"); //$NON-NLS-1$
//        supportedFunctions.add("CURRENT_USER"); //$NON-NLS-1$
//        supportedFunctions.add("DATABASE"); //$NON-NLS-1$
//        supportedFunctions.add("FOUND_ROWS"); //$NON-NLS-1$
//        supportedFunctions.add("LAST_INSERT_ID"); //$NON-NLS-1$
//        supportedFunctions.add("ROW_COUNT"); //$NON-NLS-1$
//        supportedFunctions.add("SCHEMA"); //$NON-NLS-1$
//        supportedFunctions.add("SESSION_USER"); //$NON-NLS-1$
//        supportedFunctions.add("SYSTEM_USER"); //$NON-NLS-1$
//        supportedFunctions.add("USER"); //$NON-NLS-1$
//        supportedFunctions.add("VERSION"); //$NON-NLS-1$
//        
//        // Misc.
//        supportedFunctions.add("DEFAULT"); //$NON-NLS-1$
//        supportedFunctions.add("FORMAT"); //$NON-NLS-1$
////        supportedFunctions.add("GET_LOCK"); //$NON-NLS-1$
//        supportedFunctions.add("INET_ATON"); //$NON-NLS-1$
//        supportedFunctions.add("INET_NTOA"); //$NON-NLS-1$
////        supportedFunctions.add("IS_FREE_LOCK"); //$NON-NLS-1$
////        supportedFunctions.add("IS_USED_LOCK"); //$NON-NLS-1$
////        supportedFunctions.add("MASTER_POS_WAIT"); //$NON-NLS-1$
////        supportedFunctions.add("NAME_CONST"); //$NON-NLS-1$
////        supportedFunctions.add("RELEASE_LOCK"); //$NON-NLS-1$
////        supportedFunctions.add("SLEEP"); //$NON-NLS-1$
//        supportedFunctions.add("UUID"); //$NON-NLS-1$
//        supportedFunctions.add("VALUES"); //$NON-NLS-1$
        return supportedFunctions;
    }

    public boolean supportsFullOuterJoins() {
        return false;
    }
    
    public boolean supportsAggregatesDistinct() {
        return false;
    }
        
    public boolean supportsRowLimit() {
        return true;
    }
    public boolean supportsRowOffset() {
        return true;
    }
}
