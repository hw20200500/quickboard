/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.quickboard;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.example.quickboard.env.ImageUtils;
import com.example.quickboard.env.Logger;
import com.google.android.gms.location.LocationRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.PathOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class CameraActivity extends AppCompatActivity
    implements OnImageAvailableListener, OnMapReadyCallback,
        Camera.PreviewCallback,
//        CompoundButton.OnCheckedChangeListener,
        View.OnClickListener {
  private static final Logger LOGGER = new Logger();

  private static final int PERMISSIONS_REQUEST = 1;

  private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
  private static final String ASSET_PATH = "";
  protected int previewWidth = 0;
  protected int previewHeight = 0;
  private boolean debug = false;
  protected Handler handler;
  private HandlerThread handlerThread;
  private boolean useCamera2API;
  private boolean isProcessingFrame = false;
  private byte[][] yuvBytes = new byte[3][];
  private int[] rgbBytes = null;
  private int yRowStride;
  protected int defaultModelIndex = 0;
  protected int defaultDeviceIndex = 0;
  private Runnable postInferenceCallback;
  private Runnable imageConverter;
  protected ArrayList<String> modelStrings = new ArrayList<String>();
  Double lat;
  Double lon;
  private LinearLayout bottomSheetLayout;
  private LinearLayout gestureLayout;
  private BottomSheetBehavior<LinearLayout> sheetBehavior;

  protected TextView frameValueTextView, cropValueTextView, inferenceTimeTextView;
  protected ImageView bottomSheetArrowImageView;
  private ImageView plusImageView, minusImageView;
  protected ListView deviceView;
  protected TextView threadsTextView;
  protected ListView modelView;
  protected TextView road_text;
  protected LinearLayout message_layout;
  protected TextView message_text;
  /** Current indices of device and model. */
  int currentDevice = -1;
  int currentModel = -1;
  int currentNumThreads = -1;
  public static PathOverlay path;
  NaverMap navermap;
  public static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
  String json_str;

  int n = 0;
  public static FusedLocationSource locationSource;
  ArrayList<String> deviceStrings = new ArrayList<String>();

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    LOGGER.d("onCreate " + this);
    super.onCreate(null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.tfe_od_activity_camera);

    if (hasPermission()) {
      setFragment();
    } else {
      requestPermission();
    }

    deviceView = findViewById(R.id.device_list);
    deviceStrings.add("CPU");
    deviceStrings.add("GPU");
    deviceStrings.add("NNAPI");
    deviceView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    ArrayAdapter<String> deviceAdapter =
            new ArrayAdapter<>(
                    CameraActivity.this , R.layout.deviceview_row, R.id.deviceview_row_text, deviceStrings);
    deviceView.setAdapter(deviceAdapter);
    deviceView.setItemChecked(defaultDeviceIndex, true);
    currentDevice = defaultDeviceIndex;
    deviceView.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {
              @Override
              public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                updateActiveModel();
              }
            });
    road_text = (TextView) findViewById(R.id.road_text);
    message_layout = findViewById(R.id.message_layout);
    message_text = findViewById(R.id.message_text);
    bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
    gestureLayout = findViewById(R.id.gesture_layout);
    sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
    bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);
    modelView = findViewById((R.id.model_list));

    initMap();
    locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);


    modelStrings = getModelStrings(getAssets(), ASSET_PATH);
    modelView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
    ArrayAdapter<String> modelAdapter =
            new ArrayAdapter<>(
                    CameraActivity.this , R.layout.listview_row, R.id.listview_row_text, modelStrings);
    modelView.setAdapter(modelAdapter);
    modelView.setItemChecked(defaultModelIndex, true);
    currentModel = defaultModelIndex;
    modelView.setOnItemClickListener(
            new AdapterView.OnItemClickListener() {
              @Override
              public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                updateActiveModel();
              }
            });

    ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
    vto.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
              gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            } else {
              gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
            //                int width = bottomSheetLayout.getMeasuredWidth();
            int height = gestureLayout.getMeasuredHeight();

            sheetBehavior.setPeekHeight(height);
          }
        });
    sheetBehavior.setHideable(false);

    sheetBehavior.setBottomSheetCallback(
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            switch (newState) {
              case BottomSheetBehavior.STATE_HIDDEN:
                break;
              case BottomSheetBehavior.STATE_EXPANDED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                }
                break;
              case BottomSheetBehavior.STATE_COLLAPSED:
                {
                  bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                }
                break;
              case BottomSheetBehavior.STATE_DRAGGING:
                break;
              case BottomSheetBehavior.STATE_SETTLING:
                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                break;
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });



  }
  public void read_guide_json(String json, String points) {
    //json 자료 가져오기
    try {
      //배열로된 자료를 가져올때
      JSONArray guideArray = new JSONArray(json);
      JSONArray guidePoint = new JSONArray(points);
      Log.d("guide", guideArray.getString(0));
      Log.d("guide", guidePoint.getString(0));
      Log.d("guide_inst", guideArray.getJSONObject(0).getString("instructions"));
      path = new PathOverlay();

      LocationRequest locationRequest = LocationRequest.create()
              .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
              .setInterval(1000);
      final int[] n = {0};
      navermap.addOnLocationChangeListener(new NaverMap.OnLocationChangeListener() {
        @Override
        public void onLocationChange(@NonNull Location location) {
          try {
            LinearLayout guide_layout = findViewById(R.id.guide_layout);
            ImageView guide_img = findViewById(R.id.guide_img);
            TextView guide_message = findViewById(R.id.guide_message);
            TextView road_text = findViewById(R.id.road_text);

            float distance = location.distanceTo(new Location(""){{setLatitude(guidePoint.getJSONArray(n[0]).getDouble(1));
                                                          setLongitude(guidePoint.getJSONArray(n[0]).getDouble(0));}});
            if (distance <= 10.0f) {
              String guide = guideArray.getJSONObject(n[0]).getString("instructions").toString();
              Log.d("Instruction:", guide);
              // Toast.makeText(CameraActivity.this, guide+"하세요", Toast.LENGTH_SHORT).show();

              guide_layout.setVisibility(View.VISIBLE);
              guide_message.setText(guide+"하세요");
              if (guide.contains("좌회전")) {
                guide_img.setImageResource(R.drawable.turn_left);
                if (road_text.getText().equals("차도")) {
                  Toast.makeText(CameraActivity.this, "주위를 잘 살피고 좌회전 하세요.", Toast.LENGTH_SHORT).show();
                }

              } else if (guide.contains("우회전")) {
                guide_img.setImageResource(R.drawable.turn_right);

                Animation blinkAnimation = AnimationUtils.loadAnimation(CameraActivity.this, R.anim.blink_anim);
                guide_img.startAnimation(blinkAnimation);
                if (road_text.getText().equals("차도")) {
                  Toast.makeText(CameraActivity.this, "주위를 잘 살피고 우회전 하세요.", Toast.LENGTH_SHORT).show();
                }
              } else if (guide.equals("목적지")) {
                Toast.makeText(CameraActivity.this, "목적지에 도착하였습니다. 안내를 종료합니다.", Toast.LENGTH_SHORT).show();
              }

              n[0]++;
            } else {
              guide_img.clearAnimation();
              guide_layout.setVisibility(View.GONE);
            }
            /*if (location.getLongitude()==guidePoint.getJSONArray(n[0]).getDouble(0) &&
                    location.getLatitude()==guidePoint.getJSONArray(n[0]).getDouble(1)) {
              String guide = guideArray.getJSONObject(n[0]).getString("instructions").toString();
              Log.d("Instruction:", guide);
              // Toast.makeText(CameraActivity.this, guide+"하세요", Toast.LENGTH_SHORT).show();
              n[0]++;
              guide_layout.setVisibility(View.VISIBLE);
              guide_message.setText(guide+"하세요");
              if (guide.contains("좌회전")) {
                guide_img.setImageResource(R.drawable.turn_left);
                if (road_text.getText().equals("차도")) {
                  Toast.makeText(CameraActivity.this, "주위를 잘 살피고 좌회전 하세요.", Toast.LENGTH_SHORT).show();
                }

                guide_message.setText(guide+"하세요");
              } else if (guide.contains("우회전")) {
                guide_img.setImageResource(R.drawable.turn_right);

                if (road_text.getText().equals("차도")) {
                  Toast.makeText(CameraActivity.this, "주위를 잘 살피고 우회전 하세요.", Toast.LENGTH_SHORT).show();
                }
              } else if (guide.equals("목적지")) {
                Toast.makeText(CameraActivity.this, "목적지에 도착하였습니다. 안내를 종료합니다.", Toast.LENGTH_SHORT).show();
              }
            } else {
              guide_layout.setVisibility(View.GONE);
            }*/
          } catch (JSONException e) {
            throw new RuntimeException(e);
          }
        }
      });

      for (int i = 0; i<guideArray.length(); i++) {

      }




    }  catch (JSONException e) {
      e.printStackTrace();
    }
  }
  public void read_json(String json) {
    //json 자료 가져오기
    try {
      //배열로된 자료를 가져올때
      JSONArray pathArray = new JSONArray(json);
      int n = 0;

      Log.d("path", pathArray.getString(0));
      path = new PathOverlay();

      List<LatLng> coords = new ArrayList<>();

      for (int i = 0; i<pathArray.length(); i++) {
        LatLng latLng = new LatLng(pathArray.getJSONArray(i).getDouble(1), pathArray.getJSONArray(i).getDouble(0));
        coords.add(i,latLng);
      }

      Log.d("Coords", coords.toString());
      path.setCoords(coords);
      path.setOutlineWidth(0);
      path.setWidth(40);
            /*path.setColor(Color.rgb(90, 156, 242));
            path.setPassedColor(Color.GRAY);*/
      path.setColor(Color.argb(50, 90, 156, 242));
      path.setPassedColor(Color.LTGRAY);

      path.setMap(navermap);
      // navermap.setMapType(NaverMap.MapType.Navi);
      navermap.setLayerGroupEnabled(navermap.LAYER_GROUP_BICYCLE, true);


    }  catch (JSONException e) {
      e.printStackTrace();
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
    mapFragment.getMapAsync( this);

  }
  @Override
  public void onMapReady(@NonNull NaverMap naverMap) {
    this.navermap = naverMap;
    set_user_location(navermap);

    Intent getIntent = getIntent();
    json_str = getIntent.getStringExtra("json");
    String guide_json_str = getIntent.getStringExtra("guide_json");
    String guidePoints = getIntent.getStringExtra("guide_points");
    if (json_str!=null) {
      read_json(json_str);
      read_guide_json(guide_json_str, guidePoints);
    }
  }
  private void set_user_location(NaverMap navermap) {
    navermap.setLocationSource(locationSource);
    navermap.setLocationTrackingMode(LocationTrackingMode.Face);
    navermap.addOnLocationChangeListener(location -> {
      lat = location.getLatitude();
      lon = location.getLongitude();
      float bearing = location.getBearing();
      if (bearing<=180) {
        bearing +=180;
      } else {
        bearing -=180;
      }
      CameraPosition cameraPosition = new CameraPosition(
              new LatLng(lat, lon), // 대상 지점
              17.5, // 줌 레벨
              0,
              location.getBearing()
      );
      CameraUpdate cameraUpdate = CameraUpdate.toCameraPosition(cameraPosition).animate(CameraAnimation.Easing);
      navermap.moveCamera(cameraUpdate);

    });

  }

  protected ArrayList<String> getModelStrings(AssetManager mgr, String path){
    ArrayList<String> res = new ArrayList<String>();
    try {
      String[] files = mgr.list(path);
      for (String file : files) {
        String[] splits = file.split("\\.");
        if (splits[splits.length - 1].equals("tflite")) {
          res.add(file);
        }
      }

    }
    catch (IOException e){
      System.err.println("getModelStrings: " + e.getMessage());
    }
    return res;
  }

  protected void set_text(String road) {
    message_layout.setVisibility(View.GONE);
    if (road.equals("sidewalk")) {
      road_text.setText("인도");
      message_text.setText("인도로 다니지 마세요.");
      message_text.setLayoutParams(new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
      ));

      message_layout.setVisibility(View.VISIBLE);
      message_layout.setLayoutParams(new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
      ));
    }
    else if(road.equals("crosswalk")) {
      road_text.setText("횡단보도");
      message_text.setText("횡단보도를 이용할 때는 서행하세요.");
      message_text.setLayoutParams(new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
      ));

      message_layout.setVisibility(View.VISIBLE);
      message_layout.setLayoutParams(new ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.WRAP_CONTENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
      ));
    }
    else if(road.equals("bicycle road")) {
      road_text.setText("자전거도로");
    }
    else if(road.equals("car road")||road.equals("edge of a car road")) {
      road_text.setText("차도");
    }
  }
  protected int[] getRgbBytes() {
    imageConverter.run();
    return rgbBytes;
  }


  /** Callback for android.hardware.Camera API */
  @Override
  public void onPreviewFrame(final byte[] bytes, final Camera camera) {
    if (isProcessingFrame) {
      LOGGER.w("Dropping frame!");
      return;
    }

    try {
      // Initialize the storage bitmaps once when the resolution is known.
      if (rgbBytes == null) {
        Camera.Size previewSize = camera.getParameters().getPreviewSize();
        previewHeight = previewSize.height;
        previewWidth = previewSize.width;
        rgbBytes = new int[previewWidth * previewHeight];
        onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
      }
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      return;
    }

    isProcessingFrame = true;
    yuvBytes[0] = bytes;
    yRowStride = previewWidth;

    imageConverter =
        new Runnable() {
          @Override
          public void run() {
            ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
          }
        };

    postInferenceCallback =
        new Runnable() {
          @Override
          public void run() {
            camera.addCallbackBuffer(bytes);
            isProcessingFrame = false;
          }
        };
    processImage();
  }

  /** Callback for Camera2 API */
  @Override
  public void onImageAvailable(final ImageReader reader) {
    // We need wait until we have some size from onPreviewSizeChosen
    if (previewWidth == 0 || previewHeight == 0) {
      return;
    }
    if (rgbBytes == null) {
      rgbBytes = new int[previewWidth * previewHeight];
    }
    try {
      final Image image = reader.acquireLatestImage();

      if (image == null) {
        return;
      }

      if (isProcessingFrame) {
        image.close();
        return;
      }
      isProcessingFrame = true;
      Trace.beginSection("imageAvailable");
      final Plane[] planes = image.getPlanes();
      fillBytes(planes, yuvBytes);
      yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();

      imageConverter =
          new Runnable() {
            @Override
            public void run() {
              ImageUtils.convertYUV420ToARGB8888(
                  yuvBytes[0],
                  yuvBytes[1],
                  yuvBytes[2],
                  previewWidth,
                  previewHeight,
                  yRowStride,
                  uvRowStride,
                  uvPixelStride,
                  rgbBytes);
            }
          };

      postInferenceCallback =
          new Runnable() {
            @Override
            public void run() {
              image.close();
              isProcessingFrame = false;
            }
          };

      processImage();
    } catch (final Exception e) {
      LOGGER.e(e, "Exception!");
      Trace.endSection();
      return;
    }
    Trace.endSection();
  }

  @Override
  public synchronized void onStart() {
    LOGGER.d("onStart " + this);
    super.onStart();
  }

  @Override
  public synchronized void onResume() {
    LOGGER.d("onResume " + this);
    super.onResume();

    handlerThread = new HandlerThread("inference");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  @Override
  public synchronized void onPause() {
    LOGGER.d("onPause " + this);

    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      LOGGER.e(e, "Exception!");
    }

    super.onPause();
  }

  @Override
  public synchronized void onStop() {
    LOGGER.d("onStop " + this);
    super.onStop();
  }

  @Override
  public synchronized void onDestroy() {
    LOGGER.d("onDestroy " + this);
    super.onDestroy();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      final int requestCode, final String[] permissions, final int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSIONS_REQUEST) {
      if (allPermissionsGranted(grantResults)) {
        setFragment();
      } else {
        requestPermission();
      }
    }
  }

  private static boolean allPermissionsGranted(final int[] grantResults) {
    for (int result : grantResults) {
      if (result != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  private boolean hasPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
    } else {
      return true;
    }
  }

  private void requestPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
        Toast.makeText(
                CameraActivity.this,
                "Camera permission is required for this demo",
                Toast.LENGTH_LONG)
            .show();
      }
      requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
    }
  }

  // Returns true if the device supports the required hardware level, or better.
  private boolean isHardwareLevelSupported(
      CameraCharacteristics characteristics, int requiredLevel) {
    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
      return requiredLevel == deviceLevel;
    }
    // deviceLevel is not LEGACY, can use numerical sort
    return requiredLevel <= deviceLevel;
  }

  private String chooseCamera() {
    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
    try {
      for (final String cameraId : manager.getCameraIdList()) {
        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        // We don't use a front facing camera in this sample.
        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
          continue;
        }

        final StreamConfigurationMap map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map == null) {
          continue;
        }

        // Fallback to camera1 API for internal cameras that don't have full support.
        // This should help with legacy situations where using the camera2 API causes
        // distorted or otherwise broken previews.
        useCamera2API =
            (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                || isHardwareLevelSupported(
                    characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        LOGGER.i("Camera API lv2?: %s", useCamera2API);
        return cameraId;
      }
    } catch (CameraAccessException e) {
      LOGGER.e(e, "Not allowed to access camera");
    }

    return null;
  }

  /*public static class ApiExplorer {
    public void main() throws IOException {

    }
  }*/
  protected void setFragment() {
    String cameraId = chooseCamera();

    Fragment fragment;
    if (useCamera2API) {
      CameraConnectionFragment camera2Fragment =
          CameraConnectionFragment.newInstance(
              new CameraConnectionFragment.ConnectionCallback() {
                @Override
                public void onPreviewSizeChosen(final Size size, final int rotation) {
                  previewHeight = size.getHeight();
                  previewWidth = size.getWidth();
                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
                }
              },
              this,
              getLayoutId(),
              getDesiredPreviewFrameSize());

      camera2Fragment.setCamera(cameraId);
      fragment = camera2Fragment;
    } else {
      fragment =
          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
    }

    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
  }

  protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
    // Because of the variable row stride it's not possible to know in
    // advance the actual necessary dimensions of the yuv planes.
    for (int i = 0; i < planes.length; ++i) {
      final ByteBuffer buffer = planes[i].getBuffer();
      if (yuvBytes[i] == null) {
        LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
        yuvBytes[i] = new byte[buffer.capacity()];
      }
      buffer.get(yuvBytes[i]);
    }
  }

  public boolean isDebug() {
    return debug;
  }

  protected void readyForNextImage() {
    if (postInferenceCallback != null) {
      postInferenceCallback.run();
    }
  }

  protected int getScreenOrientation() {
    switch (getWindowManager().getDefaultDisplay().getRotation()) {
      case Surface.ROTATION_270:
        return 270;
      case Surface.ROTATION_180:
        return 180;
      case Surface.ROTATION_90:
        return 90;
      default:
        return 0;
    }
  }

//  @Override
//  public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//    setUseNNAPI(isChecked);
//    if (isChecked) apiSwitchCompat.setText("NNAPI");
//    else apiSwitchCompat.setText("TFLITE");
//  }

  @Override
  public void onClick(View v) {
  }



  protected void showCropInfo(String cropInfo) {
    cropValueTextView.setText(cropInfo);
  }

  protected void showInference(String inferenceTime) {
    inferenceTimeTextView.setText(inferenceTime);
  }

  protected abstract void updateActiveModel();
  protected abstract void processImage();

  protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

  protected abstract int getLayoutId();

  protected abstract Size getDesiredPreviewFrameSize();

  protected abstract void setNumThreads(int numThreads);

  protected abstract void setUseNNAPI(boolean isChecked);
}
