package me.devsaki.hentoid.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Created by neko on 14/06/2015.
 */
public class UserRequest {

    @SerializedName("id_device")
    private String idDevice;
    @SerializedName("manufacturer")
    private String manufacturer;
    @SerializedName("model")
    private String model;
    @SerializedName("app_version_name")
    private String appVersionName;
    @SerializedName("app_version_code")
    private Integer appVersionCode;
    @SerializedName("android_version_name")
    private String androidVersionName;
    @SerializedName("android_version_code")
    private Integer androidVersionCode;

    public String getIdDevice() {
        return idDevice;
    }

    public void setIdDevice(String idDevice) {
        this.idDevice = idDevice;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAppVersionName() {
        return appVersionName;
    }

    public void setAppVersionName(String appVersionName) {
        this.appVersionName = appVersionName;
    }

    public Integer getAppVersionCode() {
        return appVersionCode;
    }

    public void setAppVersionCode(Integer appVersionCode) {
        this.appVersionCode = appVersionCode;
    }

    public String getAndroidVersionName() {
        return androidVersionName;
    }

    public void setAndroidVersionName(String androidVersionName) {
        this.androidVersionName = androidVersionName;
    }

    public Integer getAndroidVersionCode() {
        return androidVersionCode;
    }

    public void setAndroidVersionCode(Integer androidVersionCode) {
        this.androidVersionCode = androidVersionCode;
    }
}
