package com.imorning.tns.ui.map;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Poi;
import com.amap.api.navi.AmapNaviPage;
import com.amap.api.navi.AmapNaviParams;
import com.amap.api.navi.AmapNaviType;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.core.SuggestionCity;
import com.amap.api.services.help.Tip;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.imorning.tns.R;
import com.imorning.tns.activity.InputTipsActivity;
import com.imorning.tns.overlay.PoiOverlay;
import com.imorning.tns.utils.Constants;
import com.imorning.tns.utils.LocationInfo;
import com.imorning.tns.utils.NaviInfoCallback;
import com.imorning.tns.utils.ToastUtil;
import com.mylhyl.circledialog.CircleDialog;
import com.mylhyl.circledialog.callback.ConfigButton;
import com.mylhyl.circledialog.callback.ConfigDialog;
import com.mylhyl.circledialog.params.ButtonParams;
import com.mylhyl.circledialog.params.DialogParams;
import com.mylhyl.circledialog.view.listener.OnLvItemClickListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @author iMorning
 * <p>
 * 地图界面
 */
public class MapFragment extends Fragment implements AMap.OnMarkerClickListener, AMap.InfoWindowAdapter,
        PoiSearch.OnPoiSearchListener, View.OnClickListener,
        AMapLocationListener, LocationSource {

    public static final int REQUEST_CODE = 100;
    public static final int RESULT_CODE_INPUTTIPS = 101;
    public static final int RESULT_CODE_KEYWORDS = 102;
    public static final String CURRENT_CITY = "CURRENT_CITY";

    private static final String TAG = "MapFragment";
    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    //导航方式,默认是驾车
    protected AmapNaviType naviType = AmapNaviType.DRIVER;
    //声明AMapLocationClientOption对象
    protected AMapLocationClientOption mLocationOption = null;
    protected RelativeLayout searchLayout;
    //保存位置信息
    protected LocationInfo currentLocationInfo = null;
    //标识，用于判断是否只显示一次定位信息和用户重新定位
    protected boolean isFirstLoc = true;
    //位置监听，当定位发生改变的时候调用
    protected OnLocationChangedListener locationChangedListener;
    //地图视图
    protected MapView mapView;
    protected AMap mAMap;
    //界面的根视图，最外层的视图
    protected View rootView;
    protected View goRootView;
    //目标地点信息
    protected LocationInfo targetLocation = null;
    private MapViewModel mViewModel;
    // 要输入的poi搜索关键字
    private String mKeyWords = "";
    // 搜索时进度条
    private ProgressDialog progDialog = null;
    // poi返回的结果
    private PoiResult poiResult;
    // Poi查询条件类
    private PoiSearch.Query query;
    // POI搜索
    private PoiSearch poiSearch;

    private TextView mKeywordsTextView;
    //搜索框中的清除键
    private ImageView mCleanKeyWords;

    public static MapFragment newInstance() {
        return new MapFragment();
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.map_fragment, container, false);
        searchLayout = rootView.findViewById(R.id.map_fragment_search_view);
        mapView = rootView.findViewById(R.id.mapview);
        setHasOptionsMenu(true);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initMapSDK();
        initMapView(savedInstanceState);
        init();
    }


    /**
     * 开始进行poi搜索
     *
     * @param keywords 关键字
     */
    public void doSearchQuery(String keywords) {
        showProgressDialog();
        int currentPage = 1;
        // 第一个参数表示搜索字符串，第二个参数表示poi搜索类型，第三个参数表示poi搜索区域（空字符串代表全国）
        query = new PoiSearch.Query(keywords, "", currentLocationInfo.getCity());
        // 设置每页最多返回多少条poiitem
        query.setPageSize(20);
        // 设置查第一页
        query.setPageNum(currentPage);

        poiSearch = new PoiSearch(getContext(), query);
        poiSearch.setOnPoiSearchListener(this);
        //异步搜索POI
        poiSearch.searchPOIAsyn();
    }

    /**
     * 当标记点被点击时
     *
     * @param marker 被点击的标记点，是一个{@link Marker}
     * @return ...
     */
    @Override
    public boolean onMarkerClick(Marker marker) {
        marker.showInfoWindow();
        return false;
    }

    @Override
    public View getInfoContents(Marker marker) {
        return null;
    }

    /**
     * 处理点击搜索出来的地点
     *
     * @param marker 标记
     * @return 视图
     */

    @Override
    public View getInfoWindow(final Marker marker) {
        goRootView = getLayoutInflater().inflate(R.layout.poikeywordsearch_uri, null);
        AppCompatTextView title = goRootView.findViewById(R.id.poi_keyword_uri_title);
        AppCompatTextView snippet = goRootView.findViewById(R.id.poi_keyword_uri_snippet);
        AppCompatButton go_button = goRootView.findViewById(R.id.poi_keyword_uri_go);
        AppCompatButton moreinfo_button = goRootView.findViewById(R.id.poi_keyword_more);
        snippet.setText(marker.getSnippet());
        title.setText(marker.getTitle());
        go_button.setOnClickListener(this);
        moreinfo_button.setOnClickListener(this);
        // TODO: 2021-04-24 修改此处
        targetLocation = new LocationInfo(marker.getPosition().latitude, marker.getPosition().longitude, null);
        return goRootView;
    }

    /**
     * poi没有搜索到数据，返回一些推荐城市的信息
     *
     * @param cities 推荐的城市列表
     */
    private void showSuggestCity(List<SuggestionCity> cities) {
        StringBuilder infomation = new StringBuilder("推荐城市\n");
        for (int i = 0; i < cities.size(); i++) {
            infomation.append("城市名称:").append(cities.get(i).getCityName()).append("城市区号:").append(cities.get(i).getCityCode()).append("城市编码:").append(cities.get(i).getAdCode()).append("\n");
        }
        ToastUtil.show(getContext(), infomation.toString());
    }


    /**
     * POI信息查询回调方法
     */
    @Override
    public void onPoiSearched(PoiResult result, int rCode) {
        dissmissProgressDialog();
        //rCode为1000代表请求正常
        if (rCode == 1000) {
            if (result != null && result.getQuery() != null) {
                // 搜索poi的结果
                if (result.getQuery().equals(query)) {
                    // 是否是同一条
                    poiResult = result;
                    // 取得搜索到的poiitems有多少页
                    ArrayList<PoiItem> poiItems = poiResult.getPois();// 取得第一页的poiitem数据，页数从数字0开始
                    List<SuggestionCity> suggestionCities = poiResult.getSearchSuggestionCitys();// 当搜索不到poiitem数据时，会返回含有搜索关键字的城市信息
                    if (poiItems != null && poiItems.size() > 0) {
                        // 清理之前的图标
                        mAMap.clear();
                        PoiOverlay poiOverlay = new PoiOverlay(mAMap, poiItems);
                        poiOverlay.removeFromMap();
                        poiOverlay.addToMap();
                        poiOverlay.zoomToSpan();
                    } else if (suggestionCities != null && suggestionCities.size() > 0) {
                        showSuggestCity(suggestionCities);
                    } else {
                        ToastUtil.show(getContext(), R.string.no_result);
                    }
                }
            } else {
                ToastUtil.show(getContext(), R.string.no_result);
            }
        } else {
            ToastUtil.showerror(getContext(), rCode);
        }

    }

    @Override
    public void onPoiItemSearched(PoiItem poiItem, int rCode) {

    }


    /**
     * 输入提示activity选择结果后的处理逻辑
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }
        if (resultCode == RESULT_CODE_INPUTTIPS) {
            mAMap.clear();
            Tip tip = data.getParcelableExtra(Constants.EXTRA_TIP);
            if (TextUtils.isEmpty(tip.getPoiID().trim())) {
                doSearchQuery(tip.getName());
            } else {
                addTipMarker(tip);
            }
            mKeywordsTextView.setText(tip.getName());
            if (!TextUtils.isEmpty(tip.getName().trim())) {
                mCleanKeyWords.setVisibility(View.VISIBLE);
            }
        } else if (resultCode == RESULT_CODE_KEYWORDS) {
            mAMap.clear();
            String keywords = data.getStringExtra(Constants.KEY_WORDS_NAME).trim();
            if (!TextUtils.isEmpty(keywords)) {
                doSearchQuery(keywords);
            }
            mKeywordsTextView.setText(keywords);
            if (!TextUtils.isEmpty(keywords)) {
                mCleanKeyWords.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * 用marker展示输入提示list选中数据
     *
     * @param tip 提示
     */
    public void addTipMarker(Tip tip) {
        if (tip == null) {
            return;
        }
        Marker mPoiMarker = mAMap.addMarker(new MarkerOptions());
        LatLonPoint point = tip.getPoint();
        if (point != null) {
            LatLng markerPosition = new LatLng(point.getLatitude(), point.getLongitude());
            mPoiMarker.setPosition(markerPosition);
            mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerPosition, 17));
        }
        mPoiMarker.setTitle(tip.getName());
        mPoiMarker.setSnippet(tip.getAddress());
    }

    /**
     * 设置地图标记
     *
     * @param location LatLng对象
     */
    public void addMapMark(LatLng location) {
        MarkerOptions markerOption = new MarkerOptions();
        //标记点位置
        markerOption.position(location);
        //标记点标题
        markerOption.title(getString(R.string.my_location));
        markerOption.draggable(false);
        //标记点图标
        markerOption.icon(BitmapDescriptorFactory.fromBitmap(
                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_location)));
        mAMap.addMarker(markerOption);
    }

    /**
     * 点击事件回调方法
     *
     * @param v 被点击的视图
     */
    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_keywords:
                Intent intent = new Intent(getActivity(), InputTipsActivity.class);
                intent.putExtra(CURRENT_CITY, currentLocationInfo.getCity());
                startActivityForResult(intent, REQUEST_CODE);
                break;
            //点击了清除按钮
            case R.id.clean_keywords:
                mKeywordsTextView.setText("");
                mAMap.clear();
                mCleanKeyWords.setVisibility(View.GONE);
                break;
            //点击导航（去这儿）按钮
            case R.id.poi_keyword_uri_go:
                startNavi(currentLocationInfo, targetLocation, null);
                break;
            default:
                break;
        }
    }

    /**
     * 启动导航界面
     *
     * @param startLocation 起始点
     * @param endLocation   目标地
     * @param wayList       一些想要经过的点 {@link List<Poi>}
     */
    protected void startNavi(LocationInfo startLocation, LocationInfo endLocation, List<Poi> wayList) {
        if (startLocation == null) {
            ToastUtil.show(getContext(), getString(R.string.error_start));
            return;
        }
        if (endLocation == null) {
            ToastUtil.show(getContext(), getString(R.string.error_traget_location));
            return;
        }
        LatLng startLatLng = new LatLng(currentLocationInfo.getLatitudel(), currentLocationInfo.getLongitude());
        LatLng endLatLng = new LatLng(targetLocation.getLatitudel(), targetLocation.getLongitude());
        if (startLatLng.equals(endLatLng)) {
            ToastUtil.show(getContext(), getString(R.string.start_is_target));
            return;
        }
        final String[] items = {"驾车", "步行", "骑行"};
        new CircleDialog.Builder()
                .configDialog(params -> {
                    //增加弹出动画
                    params.animStyle = R.style.dialogWindowAnim;
                })
                .setTitle("选择出行方式")
                .setTitleColor(Color.BLUE)
                .setItems(items, (parent, view, position, id) -> {
                    switch (position) {
                        case 0:
                            naviType = AmapNaviType.DRIVER;
                            break;
                        case 1:
                            naviType = AmapNaviType.WALK;
                            break;
                        case 2:
                            naviType = AmapNaviType.RIDE;
                            break;
                        default:
                            naviType = AmapNaviType.DRIVER;
                            break;
                    }
                    AmapNaviParams params = new AmapNaviParams(new Poi(currentLocationInfo.getCity(), startLatLng, ""), wayList,
                            new Poi("", endLatLng, ""), naviType);
                    params.setUseInnerVoice(true);
                    AmapNaviPage amapNaviPage = AmapNaviPage.getInstance();
                    amapNaviPage.showRouteActivity(getContext(), params, new NaviInfoCallback(getContext()));
                    return true;
                })
                .setNegative(getString(android.R.string.cancel), null)
                .configNegative(params -> {
                    //取消按钮字体颜色
                    params.textColor = Color.RED;
                })
                .show(getParentFragmentManager());
    }

    /**
     * 初始化地图SDK
     */
    public void initMapSDK() {
        //初始化定位
        mLocationClient = new AMapLocationClient(requireContext());
        //设置定位回调监听
        mLocationClient.setLocationListener(this);
        //初始化AMapLocationClientOption对象
        mLocationOption = new AMapLocationClientOption();
        //设置定位场景，目前支持三种场景（签到、出行、运动，默认无场景）
        mLocationOption.setLocationPurpose(AMapLocationClientOption.AMapLocationPurpose.SignIn);
        //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //关闭缓存机制
        mLocationOption.setLocationCacheEnable(false);
        //获取一次定位结果,该方法默认为false。
        mLocationOption.setOnceLocation(true);
        //获取最近3s内精度最高的一次定位结果,设置setOnceLocationLatest(boolean b)接口为true，启动定位时SDK会返回最近3s内精度最高的一次定位结果。
        //如果设置其为true，setOnceLocation(boolean b)接口也会被设置为true，反之不会，默认为false。
        //mLocationOption.setOnceLocationLatest(true);
        //设置定位间隔,单位毫秒,默认为2000ms，最低1000ms。
        mLocationOption.setInterval(1000);
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption.setNeedAddress(true);
        if (null != mLocationClient) {
            mLocationClient.setLocationOption(mLocationOption);
            //设置场景模式后最好调用一次stop，再调用start以保证场景模式生效
            mLocationClient.stopLocation();
            mLocationClient.startLocation();
        }
    }

    /**
     * 初始化MapView及其相关设置
     *
     * @param savedInstanceState 保存的数据
     */
    public void initMapView(Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);// 此方法必须重写
        mAMap = mapView.getMap();
        // 显示实时交通状况
        mAMap.setTrafficEnabled(true);
        //地图模式可选类型：MAP_TYPE_NORMAL,MAP_TYPE_SATELLITE,MAP_TYPE_NIGHT
        mAMap.setMapType(AMap.MAP_TYPE_NORMAL);
        mAMap.setLocationSource(this);
        UiSettings uiSettings = mAMap.getUiSettings();
        //隐藏定位按键
        uiSettings.setMyLocationButtonEnabled(false);
        //显示缩放图标
        uiSettings.setZoomControlsEnabled(true);
        //设置为可以使用缩放手势
        uiSettings.setZoomGesturesEnabled(true);
        //设置为可以使用滑动手势
        uiSettings.setScrollGesturesEnabled(true);
        //设置为不可以使用旋转手势
        uiSettings.setRotateGesturesEnabled(false);
        //定位图标可以点击
        mAMap.setMyLocationEnabled(true);
        //设置默认地点为西安
        currentLocationInfo = new LocationInfo(Constants.XIAN.latitude, Constants.XIAN.longitude, Constants.DEFAULT_CITY);
    }

    /**
     * 初始化其他杂项
     */
    public void init() {
        mKeyWords = "";
        mViewModel = new ViewModelProvider(this).get(MapViewModel.class);
        mCleanKeyWords = rootView.findViewById(R.id.clean_keywords);
        mCleanKeyWords.setOnClickListener(this);
        mKeywordsTextView = rootView.findViewById(R.id.main_keywords);
        mKeywordsTextView.setOnClickListener(this);
        mAMap.setOnMarkerClickListener(this);// 添加点击marker监听事件
        mAMap.setInfoWindowAdapter(this);// 添加显示infowindow监听事件
        mAMap.getUiSettings().setRotateGesturesEnabled(false);
    }

    /**
     * 声明定位回调监听器
     * 可以判断AMapLocation对象不为空，当定位错误码类型为0时定位成功。
     *
     * @param aMapLocation aMapLocation对象
     */
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                //可在其中解析amapLocation获取相应内容。
                currentLocationInfo = new LocationInfo(aMapLocation);
                // 如果不设置标志位，此时再拖动地图时，它会不断将地图移动到当前的位置
                if (isFirstLoc) {
                    //设置缩放级别
                    mAMap.moveCamera(CameraUpdateFactory.zoomTo(17));
                    //将地图移动到定位点
                    mAMap.moveCamera(CameraUpdateFactory.changeLatLng(
                            new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude())));
                    //点击定位按钮 能够将地图的中心移动到定位点
                    locationChangedListener.onLocationChanged(aMapLocation);
                    //添加图钉
                    addMapMark(new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude()));
                    isFirstLoc = false;
                }
            } else {
                //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                Log.e(TAG, "onLocationChanged: location Error, ErrCode:" + aMapLocation.getErrorCode()
                        + ", errInfo:" + aMapLocation.getErrorInfo());
            }
        }
    }


    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        locationChangedListener = onLocationChangedListener;
    }

    @Override
    public void deactivate() {
        locationChangedListener = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，实现地图生命周期管理
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，实现地图生命周期管理
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        if (mAMap != null) mAMap.clear();
        //停止定位后，本地定位服务并不会被销毁
        mLocationClient.stopLocation();
        //销毁定位客户端，同时销毁本地定位服务。
        mLocationClient.onDestroy();
        super.onDestroy();
    }

    /**
     * 显示进度框
     */
    public void showProgressDialog() {
        progDialog = new ProgressDialog(getContext());
        progDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progDialog.setIndeterminate(false);
        progDialog.setCancelable(false);
        progDialog.setMessage("正在搜索:\n" + mKeyWords);
        progDialog.show();
    }

    /**
     * 隐藏进度框
     */
    public void dissmissProgressDialog() {
        if (progDialog != null) {
            progDialog.dismiss();
        }
    }

    public void showNaviTypeChooser() {
        //创建item
        final String[] items3 = new String[]{"条目1", "条目2", "条目3", "条目4", "条目5", "条目6", "条目7", "条目8"};
        AlertDialog alertDialog3 = new AlertDialog.Builder(requireContext())
                .setTitle("选项标题")
                .setIcon(R.mipmap.ic_launcher)
                .setItems(items3, new DialogInterface.OnClickListener() { //添加列表
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(requireContext(), "点的是：" + items3[i], Toast.LENGTH_SHORT).show();
                    }
                })
                .create();
        alertDialog3.show();

    }
}