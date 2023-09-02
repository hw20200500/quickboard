package com.example.quickboard;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.Camera;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.common.util.concurrent.ListenableFuture;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.NaverMapSdk;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final int REQUEST_CODE_CAMERA_PERMISION = 200;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };
    public static FusedLocationSource locationSource;
    public static NaverMap naverMap;
    private Context mContext;

    private static final String NAVER_CLIENT_ID = "sim8vwzufn";

    private LocationManager locationManager;
//    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static Camera mCamera;
    private int RESULT_PERMISSIONS=100;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private Interpreter tflite;
    private MappedByteBuffer tfliteModel;

    public static MapActivity getInstance;
    private Marker marker = new Marker();
    Double latL;
    Double lonL;
    int showing_layer = 0;
    public static Double user_lat;
    public static Double user_lon;
    public static LinearLayout search_layout;
    public static LinearLayout rout_layout;
    Double start_lat;
    Double start_lon;
    Double dest_lat;
    Double dest_lon;
    List<LatLng> coords;
    PathOverlay path1;
    private Button drive_bttn;
    int num2 = 0;
    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        //네이버 지도
//        mapView = (FragmentContainerView) findViewById(R.id.map_fragment);
        NaverMapSdk.getInstance(this).setClient(
                new NaverMapSdk.NaverCloudPlatformClient("sim8vwzufn"));

        search_layout = findViewById(R.id.search_layout);

// 위치 권한 확인
        if (hasLocationPermissions()) {
            // 권한이 이미 허용된 경우 지도 초기화

            Log.d(TAG, "위치 허용 코드: "+LOCATION_PERMISSION_REQUEST_CODE);
            initMap();

        } else {
            // 권한 요청
            // 위치 권한 요청
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
//        cameraProviderFuture = ProcessCameraProvider.getInstance(mContext);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        LinearLayout drive_layout = findViewById(R.id.drive_layout);
        drive_bttn = findViewById(R.id.drive_bttn);


        previewView = findViewById(R.id.previewView);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    REQUEST_CODE_CAMERA_PERMISION
            );
        } else {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this);

            // 카메라 프리뷰를 시작합니다.
            startCameraPreview();
        }


        // 카메라 프리뷰 시작 버튼 클릭 이벤트 처리
        drive_bttn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // drive_bttn.setVisibility(View.GONE);
                // drive_layout.setVisibility(View.VISIBLE);
                Intent intent = new Intent(MapActivity.this, DetectorActivity.class);
                startActivity(intent);
            }
        });

        Button drive_finish_bttn = findViewById(R.id.drive_finish_bttn);
        drive_finish_bttn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drive_layout.setVisibility(View.GONE);
                drive_bttn.setVisibility(View.VISIBLE);
            }


        });
    }


    private void startCameraPreview() {
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
//        tflite();
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        androidx.camera.core.Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

    }





    private boolean hasLocationPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    public void start_navi(View view) {
        JSONArray pathArray = Bottom_LocationInform.pathArray;
        JSONArray guideArray = Bottom_LocationInform.guideArray;
        ArrayList<JSONArray> guidePoints = Bottom_LocationInform.guide_points;
        Intent intent = new Intent(this, DetectorActivity.class);
        intent.putExtra("json", pathArray.toString());
        intent.putExtra("guide_json", guideArray.toString());
        intent.putExtra("guide_points", guidePoints.toString());

        startActivity(intent);
    }

    public void show_bicycleRoad(View view) {

        if (showing_layer==0) {
            naverMap.setLayerGroupEnabled(naverMap.LAYER_GROUP_BICYCLE, true);

            showing_layer++;
        } else {
            showing_layer--;
            naverMap.setLayerGroupEnabled(naverMap.LAYER_GROUP_BICYCLE, false);
        }
    }

    public void show_bicycleRoad2(View view) {
        if (num2==0) {
            String json = "";
            try {
                InputStream is = getAssets().open("json/거제자전거도로.json"); // json파일 이름
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
                coords = new ArrayList<>();
                JSONObject Object = Array.getJSONObject(9);
                start_lat = Object.getDouble("도로구간-기점(위도)");
                start_lon = Object.getDouble("도로구간-기점(경도)");
                dest_lat = Object.getDouble("도로구간-종점(위도)");
                dest_lon = Object.getDouble("도로구간-종점(경도)");
                new FetchDataTask().execute();
                num2++;

            } catch (IOException ex) {
                ex.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            path1.setMap(null);
            num2=0;
        }

    }

    public class FetchDataTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();

            String url = "https://naveropenapi.apigw.ntruss.com/map-direction-15/v1/driving?start="+start_lon+","+start_lat+"&goal="+dest_lon+","+dest_lat+"&option=traavoidcaronly";

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
            JSONArray pathArray = Object.getJSONArray("path");
            Log.d("Object", Object.toString());

            Log.d("path", pathArray.getString(0));
            path1 = new PathOverlay();



            for (int i = 0; i<pathArray.length(); i++) {
                LatLng latLng = new LatLng(pathArray.getJSONArray(i).getDouble(1), pathArray.getJSONArray(i).getDouble(0));
                coords.add(i,latLng);
            }

            // Log.d("Coords", coords.toString());
            path1.setCoords(coords);
            path1.setOutlineWidth(0);
            path1.setWidth(25);
            /*path.setColor(Color.rgb(90, 156, 242));
            path1.setPassedColor(Color.GRAY);*/
            path1.setColor(Color.RED);
            path1.setMap(naverMap);



        }  catch (JSONException e) {
            e.printStackTrace();
        }

    }
    @Override
    public void onMapReady(@NonNull NaverMap navermap) {
        this.naverMap = navermap;
        Intent intent_getiform = getIntent();
        String nameL = intent_getiform.getStringExtra("loc_name");
        String addrL= intent_getiform.getStringExtra("loc_addr");
        latL = intent_getiform.getDoubleExtra("loc_lat", 0);
        lonL = intent_getiform.getDoubleExtra("loc_lon", 0);
        String title = intent_getiform.getStringExtra("title");



        naverMap.setOnSymbolClickListener(symbol -> {
            marker.setMap(null);
            drive_bttn.setVisibility(View.GONE);
            Log.d("SYMBOL", symbol.getCaption()+" : "+symbol.getPosition());
            latL = symbol.getPosition().latitude;
            lonL = symbol.getPosition().longitude;

            marker.setPosition(new LatLng(latL, lonL));
            marker.setIconTintColor(Color.RED);
            marker.setMap(naverMap);
            show_Bottom_Location(symbol.getCaption(), "", latL, lonL);

            if (findViewById(R.id.guide_layout).getVisibility()== View.VISIBLE) {
                marker.setMap(null);
                Bottom_LocationInform.path.setMap(null);
                findViewById(R.id.guide_layout).setVisibility(View.GONE);
                findViewById(R.id.rout_layout).setVisibility(View.GONE);
            }

            return false;
        });
        findViewById(R.id.user_location_bttn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                marker.setMap(null);
                drive_bttn.setVisibility(View.VISIBLE);
                findViewById(R.id.loc_layout).setVisibility(View.GONE);

                Display display = getWindowManager().getDefaultDisplay();  // in Activity
                Point size = new Point();
                display.getRealSize(size); // or getSize(size)

                int height = size.y;
                findViewById(R.id.map_fragment).setLayoutParams(new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
                set_user_location(navermap);
            }
        });

        if (latL!=0 && lonL!=0) {
            marker.setMap(null);
            drive_bttn.setVisibility(View.GONE);
            Log.d("--point: ",latL+" / " + lonL);
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(latL, lonL))
                    .animate(CameraAnimation.Easing);
            navermap.moveCamera(cameraUpdate);
            marker.setPosition(new LatLng(latL, lonL));
            marker.setMap(navermap);
            show_Bottom_Location(nameL, addrL, latL, lonL);

        } else {
            set_user_location(navermap);
        }



    }



    private void set_user_location(NaverMap navermap) {
        navermap.setLocationSource(locationSource);

        navermap.setLocationTrackingMode(LocationTrackingMode.Follow);
        navermap.addOnLocationChangeListener(location -> {
            user_lat = location.getLatitude();
            user_lon = location.getLongitude();
            /*CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(location.getLatitude(), location.getLongitude()))
                    .animate(CameraAnimation.Easing);
            navermap.moveCamera(cameraUpdate);*/
        });

        /*CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(locationSource.getLastLocation().getLatitude(), locationSource.getLastLocation().getLongitude()))
                .animate(CameraAnimation.Easing);
        navermap.moveCamera(cameraUpdate);*/
    }

    private void show_Bottom_Location(String name, String addr, Double lat, Double lon) {
        final int[] mainLayoutHeight = {0};
        int home_height = mainLayoutHeight[0];
        FrameLayout loc_layout = findViewById(R.id.loc_layout);
        findViewById(R.id.loc_layout).setVisibility(View.VISIBLE);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;



        BottomSheetBehavior bottomSheetBehavior_loc = BottomSheetBehavior.from(loc_layout);
        bottomSheetBehavior_loc.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.main_layout_height));
        bottomSheetBehavior_loc.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {

                int bottomSheetHeight = 400;
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetHeight = (int) (bottomSheet.getHeight()) - 150;
                }
                mainLayoutHeight[0] = screenHeight - bottomSheetHeight;
                findViewById(R.id.map_fragment).setLayoutParams(new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mainLayoutHeight[0]));
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                int bottomSheetHeight = (int) (bottomSheet.getHeight() * slideOffset);
                mainLayoutHeight[0] = screenHeight - bottomSheetHeight;
                findViewById(R.id.map_fragment).setLayoutParams(new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mainLayoutHeight[0]));
            }
        });
        findViewById(R.id.loc_layout).setVisibility(View.VISIBLE);

        Bundle bundle = new Bundle();
        bundle.putString("loc_name", name);
        bundle.putString("loc_addr", addr);
        bundle.putDouble("loc_lat", lat);
        bundle.putDouble("loc_lon", lon);

        Bottom_LocationInform bottom_locationInform = new Bottom_LocationInform();
        bottom_locationInform.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().replace(R.id.loc_layout, bottom_locationInform).commit();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {

        Log.d(TAG, "요청코드 : "+requestCode+", 위치 허용 코드: "+LOCATION_PERMISSION_REQUEST_CODE);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (hasLocationPermissions()) {
                // 권한이 모두 허용된 경우 지도 초기화
                initMap();
                Log.d(TAG, "권한허용 및 위치 추적");
                naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
            } else {
                // 권한 거부됨
                Log.d(TAG, "권한거부");
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
                naverMap.addOnLocationChangeListener(location ->
                        Toast.makeText(this,
                                "NONE: " + location.getLatitude() + ", " + location.getLongitude(),
                                Toast.LENGTH_SHORT).show());
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

        if (requestCode==REQUEST_CODE_CAMERA_PERMISION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "카메라 기능 사용을 허용하지 않으면 특정 앱 기능 사용이 불가합니다.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                cameraProviderFuture = ProcessCameraProvider.getInstance(this);

                // 카메라 프리뷰를 시작합니다.
                startCameraPreview();
            }
        }
    }

    private void initMap() {
        // 지도 초기화 코드 작성
        //지도 객체 생성하기
        FragmentManager fm = getSupportFragmentManager();
        MapFragment mapFragment = (MapFragment)fm.findFragmentById(R.id.map_fragment);
        if (mapFragment==null) {
            mapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map_fragment, mapFragment).commit();
        }
        mapFragment.getMapAsync(this);

    }


    public void delete(View view) {
        findViewById(R.id.rout_layout).setVisibility(View.GONE);
        search_layout.setVisibility(View.VISIBLE);
        findViewById(R.id.loc_layout).setVisibility(View.GONE);
        marker.setMap(null);
        Bottom_LocationInform.path.setMap(null);
        findViewById(R.id.guide_layout).setVisibility(View.GONE);
    }



    // 검색창 함수
    public void searching(View view) {

        Intent intent_searching = new Intent(this, Search.class);
        startActivity(intent_searching);
    }


}