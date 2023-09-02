package com.example.quickboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.naver.maps.map.NaverMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class Search extends AppCompatActivity {
    private EditText editTextSearch;
    private FragmentManager fragmentManager;
    private FragmentTransaction transaction;
//    private FirebaseAuth firebaseAuth;
//    private FirebaseFirestore firestore;
    private searched_sub l_sub;
    private search_sub r_sub;
    public static LinearLayout loc_inform_view;

    public static LinearLayout recent_view;

    public Double longi;
    public Double lati;
    int count = 0;
    int num = 1;
    public static Context context;
    public static EditText search_bar;

    LinearLayout recent_search_layout1;
    NaverMap naverMap;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        context = this;

        Intent intent = getIntent();
        longi = intent.getDoubleExtra("longi",0);
        lati = intent.getDoubleExtra("lati",0);

//        firebaseAuth = FirebaseAuth.getInstance();

//        recent();
        getid();

        search_bar = (EditText) findViewById(R.id.edittext_search);
        recent_view = (LinearLayout) findViewById(R.id.recent_view);
        loc_inform_view = (LinearLayout) findViewById(R.id.loc_inform_view);



        // 검색 버튼 또는 엔터 키를 눌렀을 때 동작하도록 설정
        search_bar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND){
                    String text = search_bar.getText().toString();
                    read_json(text);

                    return true;
                }
                return false;
            }
        });

        // edittext의 상태 동적 할당 받음 -> edittext에 글자 입력하면 위치 관련 정보 레이아웃이 보이도록함
        // -> 입력한 글자가 없으면 최근 검색어 레이아웃이 보이도록 함.
        search_bar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                String search_text = s.toString();
                if (search_text.isEmpty()) {
                    recent_view.setVisibility(View.VISIBLE);
                    loc_inform_view.setVisibility(View.GONE);
                } else {
                    recent_view.setVisibility(View.GONE);
                    loc_inform_view.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String search_text = s.toString();
                if (search_text.isEmpty()) {
                    recent_view.setVisibility(View.VISIBLE);
                    loc_inform_view.setVisibility(View.GONE);

                } else {
                    recent_view.setVisibility(View.GONE);
                    loc_inform_view.setVisibility(View.VISIBLE);

                    loc_inform_view.removeAllViewsInLayout();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String search_text = s.toString();
                if (search_text.isEmpty()) {
                    recent_view.setVisibility(View.VISIBLE);
                    loc_inform_view.setVisibility(View.GONE);
                } else {
                    recent_view.setVisibility(View.GONE);
                    loc_inform_view.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void read_geo(String name, String addr) {
        try {
            BufferedReader bufferedReader;
            StringBuilder stringBuilder = new StringBuilder();
            String query = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/gc?request="+ URLEncoder.encode(addr, "UTF-8");
            URL url = new URL(query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (conn != null) {
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("X-NCP-APIGW-API-KEY-ID", "sim8vwzufn");
                conn.setRequestProperty("X-NCP-APIGW-API-KEY", "Q9uOZxljvQI0ojRiNPt6OpRz3544xZ7F7NizsZpA");
                conn.setDoInput(true);

                int responseCode = conn.getResponseCode();

                if (responseCode==200) {
                    bufferedReader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    bufferedReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                String line = null;
                while ((line=bufferedReader.readLine()) !=null) {
                    stringBuilder.append(line+'\n');
                }

                int indexFirst;
                int indexLast;

                indexFirst = stringBuilder.indexOf("\"x\":\"");
                indexLast = stringBuilder.indexOf("\",\"y\":");
                String lon = stringBuilder.substring(indexFirst+5, indexLast);

                indexFirst = stringBuilder.indexOf("\"y\":\"");
                indexLast = stringBuilder.indexOf("\",\"distance\":");
                String lat = stringBuilder.substring(indexFirst+5, indexLast);

                /*Double Latitude = Double.parseDouble(y);
                Double Longitude = Double.parseDouble(x);*/

                Log.d("-- 장소: ", name+" / "+addr);
//                searched(name, addr);

                bufferedReader.close();
                conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void read_json(String text) {
        //json 자료 가져오기
        String json = "";
        try {
            InputStream is = getAssets().open("json/대전광역시 위치정보_수정.json"); // json파일 이름
            int fileSize = is.available();

            byte[] buffer = new byte[fileSize];
            is.read(buffer);
            is.close();

            //json파일명을 가져와서 String 변수에 담음
            json = new String(buffer, "UTF-8");
//            Log.d("--  json = ", json);


            //배열로된 자료를 가져올때
            JSONArray Array = new JSONArray(json);//배열의 이름
//            JSONArray Array = jsonObject.getJSONArray("");//배열의 이름
            int n = 0;
            for(int i=0; i<Array.length(); i++)
            {
                JSONObject Object = Array.getJSONObject(i);
                if (Object.getString("이름").contains(text)) {
                    n++;
                    String name = Object.getString("이름");
                    String addr = Object.getString("주소");
                    if (n<=6) {

                        searched(Object.getString("이름"), Object.getString("주소"),Object.getDouble("lat"), Object.getDouble("lon"));
                    } else {
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    private void searched(String name, String address, Double lat, Double lon){

//        Double distance1 = truncateDecimal(distance, 2);

        loc_inform_view = (LinearLayout) findViewById(R.id.loc_inform_view);

        l_sub = new searched_sub(getApplicationContext());

        View l_sub = getLayoutInflater().inflate(R.layout.fragment_searched_view, null);
        TextView searched_name = l_sub.findViewById(R.id.loc_inform_text1);
        TextView searched_address = l_sub.findViewById(R.id.loc_inform_detail1);
        TextView searched_distance = l_sub.findViewById(R.id.dist1);
        TextView searched_lati = l_sub.findViewById(R.id.longi1);
        TextView searched_longi = l_sub.findViewById(R.id.lati1);

        searched_name.setText(name);
        searched_address.setText(address);
        searched_lati.setText(lat.toString());
        searched_longi.setText(lon.toString());
//        searched_distance.setText(distance1+"km");

        LinearLayout search_list = l_sub.findViewById(R.id.linear_list);
        search_list.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView click_name = search_list.findViewById(R.id.loc_inform_text1);
                TextView click_address = search_list.findViewById(R.id.loc_inform_detail1);
                TextView click_lati = l_sub.findViewById(R.id.longi1);
                TextView click_longi = l_sub.findViewById(R.id.lati1);

                String c_name = click_name.getText().toString();
                String c_addr = click_address.getText().toString();
                Double c_lat = Double.parseDouble(click_lati.getText().toString());
                Double c_lon = Double.parseDouble(click_longi.getText().toString());

                Intent go_mapview = new Intent(Search.this, MapActivity.class);
                go_mapview.putExtra("loc_name", c_name);
                go_mapview.putExtra("loc_addr", c_addr);
                go_mapview.putExtra("loc_lat", c_lat);
                go_mapview.putExtra("loc_lon", c_lon);

//                TMapMarkerItem marker_search = new TMapMarkerItem();

                startActivity(go_mapview);
            }
        });

        runOnUiThread(() -> {
            loc_inform_view.addView(l_sub);
        });

    }



    private void poisearch(String searchText){

        // 지오코딩 검색 및 주소 찾기
        /*TMapData tmapdata = new TMapData();
        TMapPoint tpoint = new TMapPoint();
        tpoint.setLatitude(lati);
        tpoint.setLongitude(longi);
        loc_inform_view = findViewById(R.id.loc_inform_view);
        tmapdata.findAllPOI(searchText, 8, new TMapData.OnFindAllPOIListener() {
            @Override
            public void onFindAllPOI(ArrayList<TMapPOIItem> poiitems) {
                if (poiitems != null && !poiitems.isEmpty()) {
                    runOnUiThread(() -> {
                        if (loc_inform_view.getChildCount() > 0) {
                            loc_inform_view.removeAllViews();
                        }
                        for(int i = 0; i < poiitems.size(); i++){
                            TMapPOIItem poiItem = poiitems.get(i);
//                            System.out.println(poiItem.getPOIPoint());
                            searched(poiItem.getPOIName(), poiItem.getPOIAddress(), poiItem.getPOIPoint().getLatitude(), poiItem.getPOIPoint().getLongitude(), poiItem.getDistance(tpoint)/1000);
                        }
                    });
                }
                else{
                    runOnUiThread(() -> Toast.makeText(Search.this, "검색 결과가 없습니다.", Toast.LENGTH_LONG).show());
                }
            }
        });*/
    }

    /*private void search(String searchText){

        if(num != 1){
            runOnUiThread(this::getid);
        }
        tmapdata.findAllPOI(searchText, 8, new TMapData.OnFindAllPOIListener() {
            @Override
            public void onFindAllPOI(ArrayList<TMapPOIItem> poiitems) {
                if (poiitems != null && !poiitems.isEmpty()) {
                    runOnUiThread(() -> {
                        TMapPOIItem firstItem = poiitems.get(0);
                        firestore = FirebaseFirestore.getInstance();
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        String email = user.getEmail();

                        DocumentReference docR = firestore.collection("최근기록DB").document(email);

                        String addressText = firstItem.getPOIName();
                        String db_address = firstItem.getPOIAddress();
                        double db_latitude = firstItem.getPOIPoint().getLatitude();
                        double db_longitude = firstItem.getPOIPoint().getLongitude();

                        System.out.println("addnum"+num);

                        String str_num = String.valueOf(num);

                        if (num == 1){
                            num++;
                        }


                        HashMap<Object,Object> hashMap = new HashMap<>();

                        hashMap.put("location_name",addressText);
                        hashMap.put("address",db_address);
                        hashMap.put("latitude",db_latitude);
                        hashMap.put("longitude",db_longitude);


                        docR.collection("최근기록").document(str_num).set(hashMap);

                    });
                }
                else{
                    runOnUiThread(() -> Toast.makeText(Search.this, "검색 결과가 없습니다.", Toast.LENGTH_LONG).show());
                }
            }
        });
    }*/
    public void getid(){
        /*FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user.getEmail();
        final int[] big = {0};
        final int a[] = new int[1];
        if (email != null) {
            CollectionReference recentCollectionRef = firestore.collection("최근기록DB").document(email).collection("최근기록");

            recentCollectionRef
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot.isEmpty()) {
                            // 도큐먼트가 없을 때
                            num = 1;
                        } else {
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                String idString = document.getId();
                                a[0] = Integer.parseInt(idString);
                                if(big[0] < a[0]){
                                    big[0] = a[0];
                                }
                                System.out.println(a[0]);
                            }
                            num = big[0];
                            num++;
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Recent", "Error retrieving recent records: " + e.getMessage());
                    });
        }*/
    }

    /*private void recent() {
        // 최근기록 검색 화면에 출력하기
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user.getEmail();
        recent_search_layout1 = findViewById(R.id.recent_search_layout1);

        if (email != null) {
            CollectionReference recentCollectionRef = firestore.collection("최근기록DB").document(email).collection("최근기록");

            recentCollectionRef
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        runOnUiThread(() -> {
                            List<List<Object>> documentList = new ArrayList<>(); // 각 문서를 담는 리스트

                            if (recent_search_layout1.getChildCount() > 0) {
                                recent_search_layout1.removeAllViews();
                            }

                            for (QueryDocumentSnapshot document : querySnapshot) {
                                List<Object> docData = new ArrayList<>(); // 문서 데이터를 담는 리스트
                                docData.add(document.getId());
                                docData.add(document.getString("location_name"));
                                docData.add(document.getString("address"));
                                docData.add(document.getDouble("latitude"));
                                docData.add(document.getDouble("longitude"));

                                documentList.add(docData);
                            }

                            // 리스트 정렬
                            Collections.sort(documentList, new Comparator<List<Object>>() {
                                @Override
                                public int compare(List<Object> doc1, List<Object> doc2) {
                                    // id를 기준으로 내림차순으로 정렬
                                    int id1 = Integer.parseInt((String) doc1.get(0));
                                    int id2 = Integer.parseInt((String) doc2.get(0));
                                    return id2 - id1;
                                }
                            });

                            // 최대 8개의 기록 화면에 출력
                            int count = 0;
                            for (List<Object> docData : documentList) {
                                recentget((String) docData.get(1), (String) docData.get(2), (Double) docData.get(3), (Double) docData.get(4));
                                count++; // 반복 횟수 증가

                                if (count == 8) {
                                    // 8번 반복 후 종료
                                    break;
                                }
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Recent", "Error retrieving recent records: " + e.getMessage());
                    });
        }
    }*/



    /*private void recentget(String name ,String address, Double lati,Double longi){

        // 최근기록 클릭 시 mapview로 가서 지도에 해당 위치에 마커 표시하기
        recent_search_layout1 = (LinearLayout) findViewById(R.id.recent_search_layout1);

        r_sub = new search_sub(getApplicationContext());

        View r_sub = getLayoutInflater().inflate(R.layout.fragment_search_sub, null);

        TextView search_name = r_sub.findViewById(R.id.recent_search_text1);
        TextView search_address = r_sub.findViewById(R.id.recent_search_address);
        TextView search_lati = r_sub.findViewById(R.id.recent_search_longi);
        TextView search_longi = r_sub.findViewById(R.id.recent_search_lati);

        search_name.setText(name);
        search_address.setText(address);
        search_lati.setText(lati.toString());
        search_longi.setText(longi.toString());

        ImageView bttn_delete = r_sub.findViewById(R.id.bttn_delete);
        bttn_delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                delete_text(view);
            }
        });
        LinearLayout search_list = r_sub.findViewById(R.id.list_search);
        search_list.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView click_name = search_list.findViewById(R.id.recent_search_text1);
                TextView click_address = search_list.findViewById(R.id.recent_search_address);
                TextView click_lati = r_sub.findViewById(R.id.recent_search_longi);
                TextView click_longi = r_sub.findViewById(R.id.recent_search_lati);

                String name = click_name.getText().toString();
                String addr = click_address.getText().toString();
                Double lat = Double.parseDouble(click_lati.getText().toString());
                Double lon = Double.parseDouble(click_longi.getText().toString());

                Intent go_mapview = new Intent(Search.this, mapview.class);
                go_mapview.putExtra("loc_name", name);
                go_mapview.putExtra("loc_addr", addr);
                go_mapview.putExtra("loc_lat", lat);
                go_mapview.putExtra("loc_lon", lon);

                TMapMarkerItem marker_search = new TMapMarkerItem();

                startActivity(go_mapview);
            }
        });


        runOnUiThread(() -> {
            recent_search_layout1.addView(r_sub);
        });



    }*/


    /*public void delete_text(View v) {
        // 최근기록 데이터 삭제하기
        LinearLayout layout = (LinearLayout) v.getParent().getParent();
        TextView textViewName = layout.findViewById(R.id.recent_search_text1);
        String name = textViewName.getText().toString();

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user.getEmail();

        if (email != null) {
            CollectionReference recentCollectionRef = firestore.collection("최근기록DB").document(email).collection("최근기록");
            Query query = recentCollectionRef.whereEqualTo("location_name", name);

            query.get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                            document.getReference().delete();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Recent", "Error deleting recent record: " + e.getMessage());
                    });
        }
        recent();
    }*/



}