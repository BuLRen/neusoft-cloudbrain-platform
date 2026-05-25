package com.xikang.common.constants;

/**
 * System-wide constants
 */
public class Constants {

    /**
     * Response codes
     */
    public static final class ResponseCode {
        public static final int SUCCESS = 200;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int METHOD_NOT_ALLOWED = 405;
        public static final int CONFLICT = 409;
        public static final int INTERNAL_SERVER_ERROR = 500;
        public static final int SERVICE_UNAVAILABLE = 503;

        private ResponseCode() {
            // Prevent instantiation
        }
    }

    /**
     * Error messages
     */
    public static final class ErrorMessage {
        public static final String UNAUTHORIZED = "Unauthorized access";
        public static final String FORBIDDEN = "Access forbidden";
        public static final String NOT_FOUND = "Resource not found";
        public static final String VALIDATION_ERROR = "Validation error";
        public static final String INTERNAL_ERROR = "Internal server error";
        public static final String SERVICE_UNAVAILABLE = "Service unavailable";

        private ErrorMessage() {
            // Prevent instantiation
        }
    }

    /**
     * Header names
     */
    public static final class Header {
        public static final String AUTHORIZATION = "Authorization";
        public static final String TOKEN = "Token";
        public static final String USER_ID = "X-User-Id";
        public static final String USER_NAME = "X-User-Name";
        public static final String REQUEST_ID = "X-Request-Id";

        private Header() {
            // Prevent instantiation
        }
    }

    /**
     * Common field names
     */
    public static final class Field {
        public static final String ID = "id";
        public static final String NAME = "name";
        public static final String STATUS = "status";
        public static final String CREATE_TIME = "createTime";
        public static final String UPDATE_TIME = "updateTime";
        public static final String CREATE_BY = "createBy";
        public static final String UPDATE_BY = "updateBy";
        public static final String REMARK = "remark";
        public static final String DELETED = "deleted";

        private Field() {
            // Prevent instantiation
        }
    }

    /**
     * Status values
     */
    public static final class Status {
        public static final int ACTIVE = 1;
        public static final int INACTIVE = 0;
        public static final int DELETED = -1;

        private Status() {
            // Prevent instantiation
        }
    }

    /**
     * JWT related constants
     */
    public static final class Jwt {
        public static final String TOKEN_PREFIX = "Bearer ";
        public static final String TOKEN_HEADER = "Authorization";

        private Jwt() {
            // Prevent instantiation
        }
    }

    /**
     * Pagination constants
     */
    public static final class Page {
        public static final int DEFAULT_PAGE = 1;
        public static final int DEFAULT_SIZE = 10;
        public static final int MAX_SIZE = 100;

        private Page() {
            // Prevent instantiation
        }
    }

    private Constants() {
        // Prevent instantiation
    }
}
