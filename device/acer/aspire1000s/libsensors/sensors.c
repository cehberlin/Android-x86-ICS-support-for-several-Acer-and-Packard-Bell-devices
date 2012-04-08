/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2010 Nitdroid Project
 * Copyright (C) 2011 Android-x86 Project
 *
 * Author: Alexey Roslyakov <alexey.roslyakov@newsycat.com>
 * Author: Christopher-Eyk Hrabia <c.hrabia@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


#define LOG_TAG "sensors"

#include <cerrno>
#include <sys/stat.h>

#include <cutils/log.h>
#include <cutils/properties.h>

#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <pthread.h>
#include <dirent.h>
#include <sys/poll.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/select.h>
#include <linux/netlink.h>
#include <dirent.h>
#include <poll.h>
#include <cutils/log.h>
#include <linux/input.h>
#include <linux/uinput.h>

#include <hardware/sensors.h>
#include <cutils/native_handle.h>
#include <cutils/sockets.h>
#include <cutils/properties.h>

/******************************************************************************/

#define SENSORS_LIS3LV20D_DEBUG 0

#define ID_BASE SENSORS_HANDLE_BASE
#define ID_ACCELERATION (ID_BASE+0)

#define SYSFS_PATH "/sys/devices/platform/lis3lv02d/"

//This driver supports the detection of laptop-mode or tablet-mode by special key events
//like it works on the Acer1825 series
// 0 = disable detection of laptop-mode/tablet-mode
// 1 = enable detection of laptop-mode/tablet-mode
#define CONVERTIBLE_DETECTION 1
#define CREATE_SW_TABLET_MODE 1
#define TABLET_MODE_KEY 185 //android key-code of tablet-mode key
#define LAPTOP_MODE_KEY 176 //android key-code of laptop-mode key
#define TABLET_MODE_SWITCH_VALUE 1
#define LAPTOP_MODE_SWITCH_VALUE 0

static int sensor_fd = -1;

#if (CONVERTIBLE_DETECTION > 0)
//file descriptor for keyboard events for recognition of special keycommands 
//which will be created by the device on changeing mode
static struct pollfd pfd;
#if (CREATE_SW_TABLET_MODE>0)
//file descriptor for switch user input device for indicating to android system current lid state
static int ufd =-1;
//store last switch value to prevent from generating switch events twice
static int last_switch_value=-1;
#endif

static int mode_conversion_factor_x = -1;
static int mode_conversion_factor_y = -1;
static int mode_conversion_factor_z = 1;
#else
static int mode_conversion_factor_x = 1;
static int mode_conversion_factor_y = 1;
static int mode_conversion_factor_z = 1;
#endif
/* store delay in us*/
static float delay = 10000;

static int
write_string(char const *file, const char const *value)
{
    int fd;
	char path[256];
    static int already_warned = 0;

	snprintf(path, sizeof(path), SYSFS_PATH "%s", file);

    fd = open(path, O_WRONLY);
    if (fd >= 0)
    {
        char buffer[200];
        int bytes = snprintf(buffer, sizeof(buffer), "%s\n", value);
        int amt = write(fd, buffer, bytes);
        close(fd);
        return amt == -1 ? -errno : 0;
    }
    else
    {
        if (already_warned == 0)
        {
            LOGE("write_int failed to open %s\n", path);
            already_warned = 1;
        }
        return -errno;
    }
}

static int
write_int(char const* file, int value)
{
	char buffer[20];
	int bytes = sprintf(buffer, "%d", value);
	return write_string(file, buffer);
}

/*
 * the following is the list of all supported sensors
 */
static const struct sensor_t sensors_list[] =
{
    {
        .name = "LIS3LV02DL 3-axis Accelerometer",
        .vendor = "STMicroelectronics",
        .version = 1,
        .handle = ID_ACCELERATION,
        .type = SENSOR_TYPE_ACCELEROMETER,
        .maxRange = (GRAVITY_EARTH * 2.3f),
        .resolution = (GRAVITY_EARTH * 2.3f) / 128.0f,
        .power = 3.0f,
        .minDelay = 10000,
        .reserved = {},
	},
};

static int sensors_get_list(struct sensors_module_t *module,
                            struct sensor_t const** list)
{
    *list = sensors_list;
    return 1;
}

static int sensors_set_delay_LIS3LV20D(struct sensors_poll_device_t *dev, int handle, int64_t ns)
{
    delay = ns/1000;
    return 0;
}

#if (CONVERTIBLE_DETECTION > 0)
//creates user event for tablet mode virtual lid switch and sets conversion factor
static void setTabletMode()
{	
    #if (CREATE_SW_TABLET_MODE>0)
    if(last_switch_value!=TABLET_MODE_SWITCH_VALUE)
    {
        last_switch_value=TABLET_MODE_SWITCH_VALUE;

        if(ufd >= 0)
        {
            struct input_event ev;
            memset(&ev, 0, sizeof(struct input_event));
	        ev.type = EV_SW;
	        ev.code = SW_TABLET_MODE;
	        ev.value = TABLET_MODE_SWITCH_VALUE;
	        if(write(ufd, &ev, sizeof(ev)) < 1)
	        {
	            LOGE("Could not write to switch sfd");
	        }else
	        {
	            LOGD("Write new tablet state to switch");
	        }
	    }
	}
    #endif

	mode_conversion_factor_x=1;
	mode_conversion_factor_y=1;
	LOGD("Tablet-Mode");
}

//creates user event for laptop mode virtual lid switch and sets conversion factor
static void setLaptopMode()
{	
    #if (CREATE_SW_TABLET_MODE>0)
    if(last_switch_value!=LAPTOP_MODE_SWITCH_VALUE)
    {
        last_switch_value=LAPTOP_MODE_SWITCH_VALUE;
        if(ufd >= 0)
        {
            struct input_event ev;
            memset(&ev, 0, sizeof(struct input_event));
	        ev.type = EV_SW;
	        ev.code = SW_TABLET_MODE;
	        ev.value = LAPTOP_MODE_SWITCH_VALUE;
	        if(write(ufd, &ev, sizeof(ev)) < 1)
	        {
	            LOGE("Could not write to switch sfd");
	        }else
	        {
	            LOGD("Write new laptop state to switch");
	        }
	    }	
	}
    #endif
	mode_conversion_factor_x=-1;
	mode_conversion_factor_y=-1;
	LOGD("Laptop-Mode");
}
#endif

/** Close the sensors device */
static int sensors_close_LIS3LV20D(struct hw_device_t *dev)
{
    struct sensors_control_device_t *device_control = (void *) dev;
    LOGD("%s\n", __func__);
    if (sensor_fd > 0) 
    {
        close(sensor_fd);
        sensor_fd = -1;
    }
#if (CONVERTIBLE_DETECTION > 0)
    if (pfd.fd > 0) 
    {
        close(pfd.fd);
        pfd.fd = -1;
    }

#if (CREATE_SW_TABLET_MODE>0) 
    if(ufd >= 0)
    {
        ioctl(ufd, UI_DEV_DESTROY);
        close(ufd);
        ufd=-1;
    }
#endif
#endif
    free(device_control);
    return 0;
}

static int sensors_activate_LIS3LV20D(struct sensors_poll_device_t *dev, int handle, int enabled)
{
    LOGD("%s\n", __func__);
    if (enabled) {
        if (sensor_fd < 0) {
            LOGD("%s\n","Open" SYSFS_PATH "position");
            sensor_fd = open(SYSFS_PATH "position", O_RDONLY | O_NONBLOCK);
            if (sensor_fd < 0) {
                LOGE("coord open failed in %s: %s", __FUNCTION__, strerror(errno));
                return -errno;
            }
        }
    }
    

#if (CONVERTIBLE_DETECTION > 0)
	pfd.fd = -1;
	const char *dirname = "/dev/input";
	DIR *dir;
	if ((dir = opendir(dirname))) {
		struct dirent* de;
		while ((de = readdir(dir))) {
			if (de->d_name[0] != 'e') // eventX
				continue;
			char name[PATH_MAX];
			snprintf(name, PATH_MAX, "%s/%s", dirname, de->d_name);
			pfd.fd = open(name, O_RDWR);
			if (pfd.fd < 0) {
				LOGE("could not open %s, %s", name, strerror(errno));
				continue;
			}
			name[sizeof(name) - 1] = '\0';
			if (ioctl(pfd.fd, EVIOCGNAME(sizeof(name) - 1), &name) < 1) {
				LOGE("could not get device name for %s, %s\n", name, strerror(errno));
				name[0] = '\0';
			}

			// TODO: parse /etc/excluded-input-devices.xml
			if (!strcmp(name, "AT Translated Set 2 keyboard")) {
				LOGI("open %s ok", name);
				break;
			}
			close(pfd.fd);
		}
		closedir(dir);
	}

#if (CREATE_SW_TABLET_MODE>0) 
	if(ufd < 0)
	{
	LOGD("try to create Acer_Lid_Switch\n");
    	ufd = open("/dev/uinput", O_WRONLY | O_NDELAY);
    	if (ufd >= 0) {
    		
    		struct uinput_user_dev uidev;
    		memset(&uidev, 0, sizeof(uidev));
    		snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "Acer_Lid_Switch");
    		uidev.id.vendor  = 0x1;
            uidev.id.product = 0x1;
    		uidev.id.version = 1;
    		uidev.id.bustype = 0x06;//BUS_VIRTUAL;//  'BUS_VIRTUAL' undeclared (first use in this function) --> Strange?
            ioctl(ufd, UI_SET_EVBIT, EV_SW);
    		ioctl(ufd, UI_SET_SWBIT, SW_TABLET_MODE);
    		if(write(ufd, &uidev, sizeof(uidev)) > 0)
    		{
        		if (ioctl(ufd, UI_DEV_CREATE, 0) < 0) {
        		    LOGE("cannot attach device\n");
        		    close(ufd);
                    ufd=-1;
        	    }else{

        	        LOGD("Acer_Lid_Switch virtual switch attached\n");
        	    }
    		}else
    		{
    		    LOGE("Could not write to uinput file descriptor\n");
        		close(ufd);
    		    ufd=-1;
    		}

    		
    	} else {
    		LOGE("could not open uinput device: %s", strerror(errno));
    	}
	}	
#endif
	pfd.events = POLLIN;
	
	//default state after boot up
    setLaptopMode();
#endif

    return 0;
}


static int sensors_poll_LIS3LV20D(struct sensors_poll_device_t *dev, sensors_event_t* event, int count)
{

    int fd = sensor_fd;
    if (fd < 1) {
        LOGE("Bad coord file descriptor: %d", fd);
        return -errno;
    }

#if (CONVERTIBLE_DETECTION > 0)
//detect mode of device
int pollres;
while ((pollres = poll(&pfd, 1, 0))) {
		if (pollres < 0) {
			LOGE("%s: poll %d error: %s", __FUNCTION__, pfd.fd, strerror(errno));
			break;
		}
		if (!(pfd.revents & POLLIN)) {
			LOGW("%s: ignore revents %d", __FUNCTION__, pfd.revents);
			continue;
		}

		struct input_event iev;
		size_t res = read(pfd.fd, &iev, sizeof(iev));
		if (res < sizeof(iev)) {
			LOGW("insufficient input data(%d)? fd=%d", res, pfd.fd);
			continue;
		}
		#if (SENSORS_LIS3LV20D_DEBUG > 0)
		LOGD("type=%d scancode=%d value=%d from fd=%d", iev.type, iev.code, iev.value, pfd.fd);
		#endif
		if (iev.type == EV_KEY) {
			switch (iev.code)
			{
				case TABLET_MODE_KEY:
                    setTabletMode();
					break;
				case LAPTOP_MODE_KEY:
                    setLaptopMode();
					break;
			}
		}
		
	}
#endif

//read accelerometer sensor
    fd_set rfds;
    char coord[20];
    int ret;
    struct timeval timeout;

    FD_ZERO(&rfds);
    FD_SET(fd, &rfds);

    /* we sleep here because we know we will have something to read */
    //LOGD("Sleeping for %lld usecs\n", delay);
    usleep(delay);
    do {
        timeout.tv_sec = 0;
        timeout.tv_usec = 1000000;
        ret = select(fd + 1, &rfds, NULL, NULL, &timeout);
    } while (ret < 0 && errno == EINTR);

    if (ret < 0) {
        LOGE("%s select error: %s", __func__, strerror(errno));
        return -errno;
    }

    lseek(fd, 0, SEEK_SET);
    ret = read(fd, coord, sizeof(coord));
    if (ret < 0) {
        LOGE("%s read error: %s", __func__, strerror(errno));
        return -errno;
    }

    int x = 0, y = 0, z = 0;
    sscanf(coord, "(%d,%d,%d)\n", &x, &y, &z);
#if (SENSORS_LIS3LV20D_DEBUG > 0)
    LOGD("Position x:%d y: %d z: %d \n",y,x,z);
#endif
    event->acceleration.x = mode_conversion_factor_x * (GRAVITY_EARTH * y)  / 1000;
    event->acceleration.y = mode_conversion_factor_y * (-GRAVITY_EARTH * x) / 1000;
    event->acceleration.z = mode_conversion_factor_z * (GRAVITY_EARTH * z)  / 1000;
    event->version = sizeof(struct sensors_event_t);
    event->type = SENSOR_TYPE_ACCELEROMETER;
    event->sensor = ID_ACCELERATION;
   
    struct timespec t;
    t.tv_sec = t.tv_nsec = 0;
    clock_gettime(CLOCK_MONOTONIC, &t);
	event->timestamp = (int64_t) ((int64_t)t.tv_sec * 1000000000LL + (int64_t) t.tv_nsec);

#if (SENSORS_LIS3LV20D_DEBUG > 0)
    LOGD("%s: sensor event %f, %f, %f\n", __FUNCTION__,
         event->acceleration.x, event->acceleration.y,
         event->acceleration.z);
#endif

    return 1;
}

/** 
    Read property "persist.sys.sensors.enabled" which indicates if the sensors library should be enabled or not
    Valid values are "no" and "yes"
*/
static bool is_sensors_available()
{
    struct stat sts;
    if (stat(SYSFS_PATH "position", &sts) == -1 && errno == ENOENT)
    {
        LOGD("Can not find "SYSFS_PATH "position. The reason is may a missing module or device.");
        return false;
    }else{
        return true;
    }
}

/******************************************************************************/

/**
 * module methods
 */

/** Open a new instance of a sensors device using name */
static int open_sensors(const struct hw_module_t* module, char const* name,
                        struct hw_device_t** device)
{
    LOGD("%s\n", __func__);
    int status = -EINVAL;

    if (!strcmp(name, SENSORS_HARDWARE_POLL)) {
    
        if(is_sensors_available() == true){
            LOGD("sensors enabled");
       
            struct sensors_poll_device_t *dev =
                malloc(sizeof(*dev));
            memset(dev, 0, sizeof(*dev));

            dev->common.tag = HARDWARE_DEVICE_TAG;
            dev->common.version = 0;
            dev->common.module = (struct hw_module_t*)module;
            dev->common.close = sensors_close_LIS3LV20D;
            dev->activate = sensors_activate_LIS3LV20D;
            dev->setDelay = sensors_set_delay_LIS3LV20D;
            dev->poll = sensors_poll_LIS3LV20D;
            
            *device = &dev->common;
            status = 0;
            fprintf(stderr, "!");
        }else{
            LOGD("sensors disabled");
        }
    }
    return status;
}

static struct hw_module_methods_t sensors_module_methods =
{
    .open =  open_sensors,
};

/*
 * The Sensors Hardware Module
 */
struct sensors_module_t HAL_MODULE_INFO_SYM =
{
    .common = {
        .tag           = HARDWARE_MODULE_TAG,
        .version_major = 3,
        .version_minor = 0,
        .id            = SENSORS_HARDWARE_MODULE_ID,
        .name          = "LIS3LV20D sensors module",
        .author        = "Alexey Roslyakov<alexey.roslyakov@newsycat.com>,Christopher-Eyk Hrabia<c.hrabia@gmail.com",
        .methods       = &sensors_module_methods,
        .dso = 0,
        .reserved = { }
    },
    .get_sensors_list = sensors_get_list
};
