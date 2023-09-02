package com.example.quickboard;

import static com.example.quickboard.MapActivity.naverMap;
import static com.example.quickboard.MapActivity.search_layout;
import static com.example.quickboard.MapActivity.user_lat;
import static com.example.quickboard.MapActivity.user_lon;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.overlay.PathOverlay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Bottom_LocationInform extends Fragment {

    Double latitude;
    Double longitude;
    boolean exist = false;
    String email;
//    Bottom_Favorite bottomFavorite;
    String title;
    String addr;
    View layout;
    public static JSONArray pathArray;
    public static JSONArray guideArray;
    int num;
    public static PathOverlay path;
    public static ArrayList<JSONArray> guide_points;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootview = inflater.inflate(R.layout.fragment_location_inform, container, false);




        return rootview;
    }

    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView loc_title = view.findViewById(R.id.loc_title);
        TextView loc_addr = view.findViewById(R.id.loc_addr);


        // mapview에서 받아온 위치 정보(이름, 주소, 위경도) 가져와서 문자열 및 double 변수에 저장
        title = this.getArguments().getString("loc_name");
        addr = this.getArguments().getString("loc_addr");
        latitude = this.getArguments().getDouble("loc_lat");
        longitude = this.getArguments().getDouble("loc_lon");
        //"loc_lon", longitude

        // 장소 이름, 주소는 각각 textview에 저장
        loc_title.setText(title);
        loc_addr.setText(addr);


        Button bttn_dest = view.findViewById(R.id.bttn_dest);

        // '도착' 버튼 클릭 시 MainActivity로 이동 + 장소 이름, 주소, 위경도 정보 같이 intent
        bttn_dest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                new FetchDataTask().execute();
            }
        });


    }

    public class FetchDataTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();

            String url = "https://naveropenapi.apigw.ntruss.com/map-direction-15/v1/driving?start="+user_lon+","+user_lat+"&goal="+longitude+","+latitude+"&option=traavoidcaronly";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("X-NCP-APIGW-API-KEY-ID", "sim8vwzufn")
                    .addHeader("X-NCP-APIGW-API-KEY", "Q9uOZxljvQI0ojRiNPt6OpRz3544xZ7F7NizsZpA")
                    .build();

            try {
                Response response = client.newCall(request).execute();
//                Log.d("Response", response.body().string());
                return response.body().string();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String responseJson) {
            if (responseJson != null) {
                // TODO: JSON 데이터 처리
                Log.d("Response", responseJson);
                read_json(responseJson);
                /*runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // UI 업데이트 코드 작성
                    }
                });*/
            } else {
                Log.e("Error", "Network request failed");
            }
        }
    }

    public void read_json(String json) {
        //json 자료 가져오기
        try {
            //배열로된 자료를 가져올때
            JSONObject Object = new JSONObject(json).getJSONObject("route").getJSONArray("traavoidcaronly").getJSONObject(0);//배열의 이름
            pathArray = Object.getJSONArray("path");
            guideArray = Object.getJSONArray("guide");
            guide_points = new ArrayList<JSONArray>();
            Log.d("Object", Object.toString());

            Log.d("path", pathArray.getString(0));
            path = new PathOverlay();

            List<LatLng> coords = new ArrayList<>();

            int n=0;
            for (int i = 0; i<pathArray.length(); i++) {
                LatLng latLng = new LatLng(pathArray.getJSONArray(i).getDouble(1), pathArray.getJSONArray(i).getDouble(0));
                coords.add(i,latLng);
                if (guideArray.getJSONObject(n).getInt("pointIndex")==i) {
                    n++;
                    guide_points.add(pathArray.getJSONArray(i));
                }
            }

            Log.d("guidePoints", guide_points.toString());
            Log.d("Coords", coords.toString());
            path.setCoords(coords);
            path.setOutlineWidth(0);
            path.setWidth(25);
            /*path.setColor(Color.rgb(90, 156, 242));
            path.setPassedColor(Color.GRAY);*/
            path.setColor(Color.argb(50, 90, 156, 242));
            path.setMap(naverMap);


            search_layout.setVisibility(View.GONE);
            getActivity().findViewById(R.id.rout_layout).setVisibility(View.VISIBLE);
            TextView start_point = getActivity().findViewById(R.id.start_point);
            start_point.setText("내 위치");
            TextView dest_point = getActivity().findViewById(R.id.dest_point);
            dest_point.setText(title);

            getActivity().findViewById(R.id.guide_layout).setVisibility(View.VISIBLE);
            getActivity().findViewById(R.id.loc_layout).setVisibility(View.GONE);
            int duration = Object.getJSONObject("summary").getInt("duration")/1000/60;

            if (duration<60) {
                TextView text_dur = getActivity().findViewById(R.id.text_dur);
                text_dur.setText(String.valueOf(duration)+"분");
            }

            int distance = Object.getJSONObject("summary").getInt("distance")/100;

            if (distance<1000) {
                TextView text_dis = getActivity().findViewById(R.id.text_dist);
                text_dis.setText(String.valueOf(distance)+"m");

            }

            naverMap.setLayerGroupEnabled(naverMap.LAYER_GROUP_BICYCLE, true);

            // String args[] = new String[0];
            // new ApiExplorer().main(args);



        }  catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public class ApiExplorer {

        public void main(String[] args) {
            new FetchDataTask().execute();
        }

        private class FetchDataTask extends AsyncTask<Void, Void, String> {

            @Override
            protected String doInBackground(Void... voids) {
                try {
                    String api_key = URLDecoder.decode("aet6OEOVHIAT9tFyQRbVjPXgxlLg/i/PpLfkqNwCL8dAk+kNqV+UQP5UBGM0tr1ExdOObPkem3XplVALPof2Uw==", "UTF-8");
                    StringBuilder urlBuilder = new StringBuilder("https://api.data.go.kr/openapi/tn_pubr_public_bike_road_api"); /*URL*/
                    urlBuilder.append("?" + URLEncoder.encode("serviceKey", "UTF-8") + "="+api_key); /*Service Key*/
                    urlBuilder.append("&" + URLEncoder.encode("pageNo", "UTF-8") + "=" + URLEncoder.encode("1", "UTF-8")); /*페이지 번호*/
                    urlBuilder.append("&" + URLEncoder.encode("numOfRows", "UTF-8") + "=" + URLEncoder.encode("100", "UTF-8")); /*한 페이지 결과 수*/
                    urlBuilder.append("&" + URLEncoder.encode("type", "UTF-8") + "=" + URLEncoder.encode("json", "UTF-8")); /*XML/JSON 여부*/
                    urlBuilder.append("&" + URLEncoder.encode("rteNm", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*노선명*/
                    urlBuilder.append("&" + URLEncoder.encode("routeNum", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*노선번호*/
                    urlBuilder.append("&" + URLEncoder.encode("ctpvNm", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*시도명*/
                    urlBuilder.append("&" + URLEncoder.encode("sggNm", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*시군구명*/
                    urlBuilder.append("&" + URLEncoder.encode("roadStPoint", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*도로구간-기점*/
                    urlBuilder.append("&" + URLEncoder.encode("roadEdPoint", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*도로구간-종점*/
                    urlBuilder.append("&" + URLEncoder.encode("roadStPointLat", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*도로구간-기점(위도)*/
                    urlBuilder.append("&" + URLEncoder.encode("roadStPointLon", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*도로구간-기점(경도)*/
                    urlBuilder.append("&" + URLEncoder.encode("roadEdPointLat", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*도로구간-종점(위도)*/
                    urlBuilder.append("&" + URLEncoder.encode("roadEdPointLon", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*도로구간-종점(경도)*/
                    urlBuilder.append("&" + URLEncoder.encode("majorStopover", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*주요경유지*/
                    urlBuilder.append("&" + URLEncoder.encode("totalLength", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*총길이(km)*/
                    urlBuilder.append("&" + URLEncoder.encode("roadWidth", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*일반도로너비(m)*/
                    urlBuilder.append("&" + URLEncoder.encode("bikeRoadWidth", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*자전거도로너비(m)*/
                    urlBuilder.append("&" + URLEncoder.encode("bikeRoadType", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*자전거도로종류*/
                    urlBuilder.append("&" + URLEncoder.encode("bikeRoadNotiChk", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*자전거도로고시유무*/
                    urlBuilder.append("&" + URLEncoder.encode("mngInstNm", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*관리기관명*/
                    urlBuilder.append("&" + URLEncoder.encode("instTelno", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*관리기관전화번호*/
                    urlBuilder.append("&" + URLEncoder.encode("crtrYmd", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*데이터기준일자*/
                    urlBuilder.append("&" + URLEncoder.encode("instt_code", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*제공기관코드*/
                    urlBuilder.append("&" + URLEncoder.encode("instt_nm", "UTF-8") + "=" + URLEncoder.encode("", "UTF-8")); /*제공기관기관명*/


                    URL url = new URL(urlBuilder.toString());
                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-type", "application/json");
                    System.out.println("Response code: " + conn.getResponseCode());
                    BufferedReader rd;
                    if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
                        rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    } else {
                        rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    }
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = rd.readLine()) != null) {
                        sb.append(line);
                    }
                    rd.close();
                    conn.disconnect();
                    return sb.toString();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                // 결과 처리
                System.out.println(result);
            }
        }
    }


}