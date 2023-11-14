package whu.edu.cs.transitnet;

import org.junit.Test;
import whu.edu.cs.transitnet.pojo.RealTimePointEntity;
import whu.edu.cs.transitnet.service.index.CubeId;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Create_index_list {
    @Test
    public void ExpTest() throws ClassNotFoundException, SQLException {
        int resolution=6;
        File TripCubeFile = new File("D:\\datasets\\elec_car\\elec_march_TClist_6.txt");

        Class.forName("com.mysql.cj.jdbc.Driver");
        String url ="jdbc:mysql://localhost:3306/test";
        Connection conn= DriverManager.getConnection(url, "root", "021212");

        //构建list第一步，先查出所有的trip_id
        Statement stat= conn.createStatement();
        ResultSet re=stat.executeQuery("SELECT trip_id FROM elec_tripids");
        List<String> tripIds=resultSetToList(re);
        int num = tripIds.size();
        for(int i=0;i<num;i++){
            String tripId = tripIds.get(i);
            ArrayList<CubeId> cubeIds = new ArrayList<>();
        }
    }

    public List<String> resultSetToList(ResultSet rs) throws SQLException {
        List<String> list = new ArrayList<String>();
        while (rs.next()) {
            list.add(rs.getString(1));
        }
        return list;
    }


}
