import java.sql.*;

public class QueryDb {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://43.139.102.203:5432/xikang_hospital";
        String user = "postgres";
        String pwd = "eA6CNaWFewsMGCyZ";

        try (Connection conn = DriverManager.getConnection(url, user, pwd)) {
            System.out.println("=== 1. 当前的 MEDICATION_FEE 行 ===");
            print(conn, "SELECT id, register_id, patient_id, patient_name, total_amount, status, pay_time FROM expense_record WHERE item_code = 'MEDICATION_FEE' ORDER BY id DESC");

            System.out.println("\n=== 2. 未发药的处方 + 应缴金额（前 15 条）===");
            print(conn,
                "SELECT r.id AS register_id, r.patient_id, r.real_name AS patient_name, " +
                "       COUNT(p.id) AS rx_count, " +
                "       COALESCE(SUM(COALESCE(d.price, 0) * COALESCE(CAST(p.drug_number AS NUMERIC), 0)), 0) AS total_amount " +
                "FROM register r " +
                "JOIN prescription p ON p.register_id = r.id " +
                "LEFT JOIN drug_info d ON d.id = p.drug_id " +
                "WHERE p.drug_state = '未发' " +
                "GROUP BY r.id, r.patient_id, r.real_name " +
                "ORDER BY r.id DESC " +
                "LIMIT 15");
        }
    }

    private static void print(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            for (int i = 1; i <= n; i++) {
                System.out.print(md.getColumnName(i));
                if (i < n) System.out.print(" | ");
            }
            System.out.println();
            System.out.println("-".repeat(80));
            while (rs.next()) {
                for (int i = 1; i <= n; i++) {
                    Object v = rs.getObject(i);
                    System.out.print(v == null ? "NULL" : v.toString());
                    if (i < n) System.out.print(" | ");
                }
                System.out.println();
            }
        }
    }
}
