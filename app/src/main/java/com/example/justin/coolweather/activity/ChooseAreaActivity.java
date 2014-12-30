package com.example.justin.coolweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.justin.coolweather.R;
import com.example.justin.coolweather.model.City;
import com.example.justin.coolweather.model.CoolWeatherDB;
import com.example.justin.coolweather.model.County;
import com.example.justin.coolweather.model.Province;
import com.example.justin.coolweather.util.HttpCallbackListener;
import com.example.justin.coolweather.util.HttpUtil;
import com.example.justin.coolweather.util.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Justin on 2014/12/30.
 */
public class ChooseAreaActivity extends Activity {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleView;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private CoolWeatherDB coolWeatherDB;
    private List<String> dataList = new ArrayList<String>();

    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;
    private Province selectedProvince;
    private City selectedCity;
    private County selectedCounty;
    private int currentLevel;

    private boolean isFromWeatherActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isFromWeatherActivity = getIntent().getBooleanExtra("from_weather_activity",false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("city_selected", false) && !isFromWeatherActivity){
            Intent intent = new Intent(this,WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.choose_area);
        listView = (ListView)findViewById(R.id.list_view);
        titleView = (TextView)findViewById(R.id.title_text);
        adapter = new ArrayAdapter<String>(this,android.R.layout.simple_expandable_list_item_1, dataList);
        listView.setAdapter(adapter);
        coolWeatherDB = CoolWeatherDB.getInstance(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int index, long arg3) {
                if (currentLevel == LEVEL_PROVINCE){
                    selectedProvince = provinceList.get(index);
                    queryCities();
                }else if(currentLevel == LEVEL_CITY){
                    selectedCity = cityList.get(index);
                    queryCounties();
                }else if (currentLevel == LEVEL_COUNTY){
                    String countyCode = countyList.get(index).getCountyCode();
                    Intent intent = new Intent(ChooseAreaActivity.this, WeatherActivity.class);
                    intent.putExtra("county_code", countyCode);
                    startActivity(intent);
                    finish();
                }
            }
        });
        queryProvinces();
    }

    /*
    * 查询全国所有省份从数据库开始查询，如果没有再去服务器查询
    * */
    private void queryProvinces(){
        provinceList = coolWeatherDB.loadProvince();
        if (provinceList.size() > 0){
            dataList.clear();
            for (Province province : provinceList){
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleView.setText("中国");
            currentLevel = LEVEL_PROVINCE;
        }else {
            queryFromServer(null,"province");
        }
    }

    /*
    * 查询选中省内所有的市，优先从数据库查询，如果没有再去服务器查询
    * */
    private void queryCities(){
        cityList = coolWeatherDB.loadCities(selectedProvince.getId());
        if (cityList.size() > 0){
            dataList.clear();
            for (City city : cityList){
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleView.setText(selectedProvince.getProvinceName());
            currentLevel = LEVEL_CITY;
        }else {
            queryFromServer(selectedProvince.getProvinceCode(),"city");
        }
    }

    /*
    * 查询选中市内的所有县，优先从数据库查询，如果没有再去服务器查询
    * */
    private void queryCounties(){
        countyList = coolWeatherDB.loadCounties(selectedCity.getId());
        if (countyList.size() > 0){
            dataList.clear();
            for (County county : countyList){
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleView.setText(selectedCity.getCityName());
            currentLevel = LEVEL_COUNTY;
        }else {
            queryFromServer(selectedCity.getCityCode(),"county");
        }
    }

    /*
    * 根据传入代号和类型从数据库查询省市县的数据*/

   private void queryFromServer(final String code, final String type){
       String address;
       if (!TextUtils.isEmpty(code)){
           address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
       }else {
           address = "http://www.weather.com.cn/data/list3/city.xml";
       }
       showProgressDialog();
       HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {
           @Override
           public void onFinish(String response) {
               boolean result = false;
               if ("province".equals(type)){
                   result = Utility.handleProvincesResponse(coolWeatherDB, response);
               }else if ("city".equals(type)){
                   result = Utility.handleCitiesResponse(coolWeatherDB, response,selectedProvince.getId());
               }else if ("county".equals(type)){
                   result =  Utility.handleCountiesResponse(coolWeatherDB, response ,selectedCity.getId());
               }
               if (result){
                   runOnUiThread(new Runnable() {
                       @Override
                       public void run() {
                           closeProgressDialog();
                           if ("province".equals(type)){
                               queryProvinces();
                           }else if ("city".equals(type)){
                               queryCities();
                           }else if ("county".equals(type)){
                               queryCounties();
                           }
                       }
                   });
               }
           }

           @Override
           public void onError(Exception e) {
               runOnUiThread(new Runnable() {
                   @Override
                   public void run() {
                       closeProgressDialog();
                       Toast.makeText(ChooseAreaActivity.this,"加载失败" ,Toast.LENGTH_SHORT).show();
                   }
               });

           }
       });
   }

    /*
    * 显示进度对话框
    * */
    private void showProgressDialog(){
        if (progressDialog == null){
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(true);
        }
        progressDialog.show();
    }
    /*
    * 关闭进度对话框
    * */
    private void closeProgressDialog(){
        if (progressDialog != null){
            progressDialog.dismiss();
        }
    }
    /*
    * 捕获back键，根据当前的级别来判断，此时应该返回市列表 省列表 还是直接退出*/

    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY){
            queryCities();
        }else if (currentLevel == LEVEL_CITY){
            queryProvinces();
        }else{
            if (isFromWeatherActivity){
                Intent intent = new Intent(this, WeatherActivity.class);
                startActivity(intent);
            }
            finish();
        }
    }
}
