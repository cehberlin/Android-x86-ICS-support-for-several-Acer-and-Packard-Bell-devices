PRODUCT_PACKAGES := $(THIRD_PARTY_APPS)
PRODUCT_PACKAGES += app
PRODUCT_PACKAGES += sensors.$(TARGET_PRODUCT)
PRODUCT_PACKAGES += libhuaweigeneric-ril


PRODUCT_COPY_FILES := \
device/common/generic_x86/GenericTouch.idc:system/usr/idc/Vendor_2087_Product_0b03.idc \
device/common/generic_x86/GenericTouch.idc:system/usr/idc/Vendor_062a_Product_7100.idc \
device/common/generic_x86/GenericTouchPad.idc:system/usr/idc/SynPS_2_Synaptics_TouchPad.idc \
$(LOCAL_PATH)/vold.fstab:system/etc/vold.fstab #\
#\
#$(LOCAL_PATH)/gapps-ics/lib/libfacelock_jni.so:system/lib/libfacelock_jni.so \
#$(LOCAL_PATH)/gapps-ics/lib/libfilterpack_facedetect.so:system/lib/libfilterpack_facedetect.so \
#$(LOCAL_PATH)/gapps-ics/lib/libflint_engine_jni_api.so:system/lib/libflint_engine_jni_api.so \
#$(LOCAL_PATH)/gapps-ics/lib/libfrsdk.so:system/lib/libfrsdk.so \
#$(LOCAL_PATH)/gapps-ics/lib/libhwui.so:system/lib/libhwui.so \
#$(LOCAL_PATH)/gapps-ics/lib/libpicowrapper.so:system/lib/libpicowrapper.so \
#$(LOCAL_PATH)/gapps-ics/lib/libspeexwrapper.so:system/lib/libspeexwrapper.so \
#$(LOCAL_PATH)/gapps-ics/lib/libvideochat_jni.so:system/lib/libvideochat_jni.so \
#$(LOCAL_PATH)/gapps-ics/lib/libvideochat_stabilize.so:system/lib/libvideochat_stabilize.so \
#$(LOCAL_PATH)/gapps-ics/lib/libvoicesearch.so:system/lib/libvoicesearch.so \
#$(LOCAL_PATH)/gapps-ics/framework/com.google.android.maps.jar:system/framework/com.google.android.maps.jar \
#$(LOCAL_PATH)/gapps-ics/framework/com.google.android.media.effects.jar:system/framework/com.google.android.media.effects.jar \
#$(LOCAL_PATH)/gapps-ics/etc/permissions/com.google.android.maps.xml:system/etc/permissions/com.google.android.maps.xml \
#$(LOCAL_PATH)/gapps-ics/etc/permissions/com.google.android.media.effects.xml:system/etc/permissions/com.google.android.media.effects.xml \
#$(LOCAL_PATH)/gapps-ics/lib/libjni_mosaic.so:system/lib/libjni_mosaic.so


PRODUCT_PROPERTY_OVERRIDES := \
    poweroff.doubleclick=1

$(call inherit-product,$(SRC_TARGET_DIR)/product/generic_x86.mk)

PRODUCT_NAME := aspire1000s
PRODUCT_DEVICE := aspire1000s
PRODUCT_MANUFACTURER := Acer

DEVICE_PACKAGE_OVERLAYS := $(LOCAL_PATH)/overlays



