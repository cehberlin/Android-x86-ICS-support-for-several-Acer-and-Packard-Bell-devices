PRODUCT_PACKAGES := $(THIRD_PARTY_APPS)
PRODUCT_PACKAGES += app
PRODUCT_PACKAGES += sensors.$(TARGET_PRODUCT)
PRODUCT_PACKAGES += libhuaweigeneric-ril


PRODUCT_COPY_FILES := \
device/common/generic_x86/GenericTouch.idc:system/usr/idc/Vendor_2087_Product_0b03.idc \
device/common/generic_x86/GenericTouch.idc:system/usr/idc/Vendor_062a_Product_7100.idc \
device/common/generic_x86/GenericTouchPad.idc:system/usr/idc/SynPS_2_Synaptics_TouchPad.idc \
\
$(LOCAL_PATH)/vold.fstab:system/etc/vold.fstab #\
#\
#$(LOCAL_PATH)/gapps-ics/system/lib/libfilterpack_facedetect.so:system/lib/libfilterpack_facedetect.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libflint_engine_jni_api.so:system/lib/libflint_engine_jni_api.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libfrsdk.so:system/lib/libfrsdk.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libgcomm_jni.so:system/lib/libgcomm_jni.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libpicowrapper.so:system/lib/libpicowrapper.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libspeexwrapper.so:system/lib/libspeexwrapper.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libvideochat_jni.so:system/lib/libvideochat_jni.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libvideochat_stabilize.so:system/lib/libvideochat_stabilize.so \
#$(LOCAL_PATH)/gapps-ics/system/lib/libvoicesearch.so:system/lib/libvoicesearch.so \
#\
#$(LOCAL_PATH)/gapps-ics/system/framework/com.google.android.maps.jar:system/framework/com.google.android.maps.jar \
#$(LOCAL_PATH)/gapps-ics/system/framework/com.google.android.media.effects.jar:system/framework/com.google.android.media.effects.jar \
#$(LOCAL_PATH)/gapps-ics/system/framework/com.google.widevine.software.drm.jar:system/framework/com.google.widevine.software.drm.jar \
#\
#$(LOCAL_PATH)/gapps-ics/system/etc/permissions/com.google.android.maps.xml:system/etc/permissions/com.google.android.maps.xml \
#$(LOCAL_PATH)/gapps-ics/system/etc/permissions/com.google.android.media.effects.xml:system/etc/permissions/com.google.android.media.effects.xml \
#\
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/de-DE_gl0_sg.bin:system/tts/lang_pico/de-DE_gl0_sg.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/de-DE_ta.bin:system/tts/lang_pico/de-DE_ta.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/es-ES_ta.bin:system/tts/lang_pico/es-ES_ta.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/es-ES_zl0_sg.bin:system/tts/lang_pico/es-ES_zl0_sg.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/fr-FR_nk0_sg.bin:system/tts/lang_pico/fr-FR_nk0_sg.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/fr-FR_ta.bin:system/tts/lang_pico/fr-FR_ta.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/it-IT_cm0_sg.bin:system/tts/lang_pico/it-IT_cm0_sg.bin \
#$(LOCAL_PATH)/gapps-ics/system/tts/lang_pico/it-IT_ta.bin:system/tts/lang_pico/it-IT_ta.bin \


PRODUCT_PROPERTY_OVERRIDES := \
    poweroff.doubleclick=1

$(call inherit-product,$(SRC_TARGET_DIR)/product/generic_x86.mk)

PRODUCT_NAME := aspire1000s
PRODUCT_DEVICE := aspire1000s
PRODUCT_MANUFACTURER := Acer

DEVICE_PACKAGE_OVERLAYS := $(LOCAL_PATH)/overlays



