/*
 * Copyright (C) 2014 The Android Open Source Project.
 *
 *        yinglovezhuzhu@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opensource.bitmaploader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.io.File;

/**
 * This class holds our bitmap caches (memory and disk).
 */
public class ImageCache {
    private static final String TAG = "ImageCache";

    // Default memory cache size
    private static final int DEFAULT_MEM_CACHE_SIZE = 1024 * 1024 * 5; // 5MB

    // Default disk cache size
    private static final int DEFAULT_DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB

    // Compression settings when writing images to disk cache
    private static final CompressFormat DEFAULT_COMPRESS_FORMAT = CompressFormat.JPEG;
    private static final int DEFAULT_COMPRESS_QUALITY = 70;

    // Constants to easily toggle various caches
    private static final boolean DEFAULT_MEM_CACHE_ENABLED = true;
    private static final boolean DEFAULT_DISK_CACHE_ENABLED = true;
    private static final boolean DEFAULT_CLEAR_DISK_CACHE_ON_START = false;
    private static ImageCacheParams mImageCacheParams = null;
    private DiskLruCache mDiskCache;
    private LruCache<String, Bitmap> mMemoryCache;

    /**
     * Creating a new ImageCache object using the specified parameters.
     *
     * @param context     The context to use
     * @param cacheParams The cache parameters to use to initialize the cache
     */
    public ImageCache(Context context, ImageCacheParams cacheParams) {
        init(context, cacheParams);
    }

    /**
     * Creating a new ImageCache object using the default parameters.
     *
     * @param context    The context to use
     * @param uniqueName A unique name that will be appended to the cache directory
     */
    public ImageCache(Context context, String uniqueName) {
        init(context, new ImageCacheParams(uniqueName));
    }

    /**
     * Find and return an existing ImageCache stored in a {@link com.opensource.bitmaploader.RetainFragment},
     * if not found a new one is created with defaults and saved to a {@link com.opensource.bitmaploader.RetainFragment}.
     *
     * @param activity   The calling {@link android.support.v4.app.FragmentActivity}
     * @param uniqueName A unique name to append to the cache directory
     * @return An existing retained ImageCache object or a new one if one did not exist.
     */
    public static ImageCache findOrCreateCache(
            final FragmentActivity activity, final File cachPath, final String uniqueName) {
        return findOrCreateCache(activity, new ImageCacheParams(cachPath, uniqueName));
    }

    /**
     * Find and return an existing ImageCache stored in a {@link com.opensource.bitmaploader.RetainFragment},
     * if not found a new one is created using the supplied params and saved to a {@link com.opensource.bitmaploader.RetainFragment}.
     *
     * @param activity    The calling {@link android.support.v4.app.FragmentActivity}
     * @param cacheParams The cache parameters to use if creating the ImageCache
     * @return An existing retained ImageCache object or a new one if one did not exist
     */
    public static ImageCache findOrCreateCache(
            final FragmentActivity activity, ImageCacheParams cacheParams) {

        // Search for, or create an instance of the non-UI RetainFragment
        final RetainFragment mRetainFragment = RetainFragment.findOrCreateRetainFragment(
                activity.getSupportFragmentManager());

        // See if we already have an ImageCache stored in RetainFragment
        ImageCache imageCache = (ImageCache) mRetainFragment.getObject();

        // No existing ImageCache, create one and store it in RetainFragment
        if (imageCache == null) {
            imageCache = new ImageCache(activity, cacheParams);
            mRetainFragment.setObject(imageCache);
        }

        return imageCache;
    }

    /**
     * Initialize the cache, providing all parameters.
     *
     * @param context     The context to use
     * @param cacheParams The cache parameters to initialize the cache
     */
    private void init(Context context, ImageCacheParams cacheParams) {
        mImageCacheParams = cacheParams;
        //get a cache floder
        final File diskCacheDir = DiskLruCache.getDiskCacheDir(context, cacheParams.cachePath, cacheParams.uniqueName);

        // Set up disk cache
        if (cacheParams.diskCacheEnabled) {
            mDiskCache = DiskLruCache.openCache(context, diskCacheDir, cacheParams.diskCacheSize);
            mDiskCache.setCompressParams(cacheParams.compressFormat, cacheParams.compressQuality);
            if (cacheParams.clearDiskCacheOnStart) {
                mDiskCache.clearCache();
            }
        }

        // Set up memory cache
        if (cacheParams.memoryCacheEnabled) {
            mMemoryCache = new LruCache<String, Bitmap>(cacheParams.memCacheSize) {
                /**
                 * Measure item size in bytes rather than units which is more practical for a bitmap
                 * cache
                 */
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return BitmapUtils.getBitmapSize(bitmap);
                }
            };
        }
    }

    public void addBitmapToCache(String data, Bitmap bitmap) {
        if (data == null || bitmap == null) {
            return;
        }

        // Add to memory cache
        if (mMemoryCache != null && mMemoryCache.get(data) == null) {
            mMemoryCache.put(data, bitmap);
        }

        // Add to disk cache
        if (mDiskCache != null && !mDiskCache.containsKey(data)) {
            mDiskCache.put(data, bitmap);
        }
    }

    public void addBitmapToMenCache(String key, Bitmap bitmap) {
        if (key == null || bitmap == null) {
            return;
        }

        // Add to memory cache
        if (mMemoryCache != null && mMemoryCache.get(key) == null) {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * Get from memory cache.
     *
     * @param data Unique identifier for which item to get
     * @return The bitmap if found in cache, null otherwise
     */
    public Bitmap getBitmapFromMemCache(String data) {
        if (mMemoryCache != null) {
            final Bitmap memBitmap = mMemoryCache.get(data);
            if (memBitmap != null) {
                if (ImageWorker.DEBUG) {
                    Log.d(TAG, "Memory cache hit");
                }
                return memBitmap;
            }
        }
        return null;
    }

    /**
     * Get from disk cache.
     *
     * @param data Unique identifier for which item to get
     * @return The bitmap if found in cache, null otherwise
     */
    public Bitmap getBitmapFromDiskCache(String data, Bitmap.Config config) {
        if (mDiskCache != null) {
            try {
                return mDiskCache.get(data, config);
            } catch (OutOfMemoryError error) {
                error.printStackTrace();
                cleanMemCache();
            }
        }
        return null;
    }

    /**
     * Get disk cache file.
     *
     * @param data
     * @return
     */
    public File getDiskCacheFile(String data) {
        if (mDiskCache == null) {
            return null;
        }
        return mDiskCache.getDiskCacheFile(data);
    }

    /**
     * Clean caches, both memory cache and disk cache.
     */
    public void cleanCaches() {
        mDiskCache.clearCache();
        mMemoryCache.evictAll();
    }

    /**
     * Clean memory cache.
     */
    public void cleanMemCache() {
        mMemoryCache.evictAll();
    }

    /**
     * Clean disk cache.
     */
    public void cleanDiskCache() {
        mDiskCache.clearCache();
    }

    /**
     * Get cache params.
     *
     * @return
     */
    public ImageCacheParams getImageCacheParams() {
        return mImageCacheParams;
    }

    /**
     * A holder class that contains cache parameters.
     */
    public static class ImageCacheParams {
        public File cachePath = null;
        public String uniqueName;
        public ImageCacheParams(String uniqueName) {
            this.uniqueName = uniqueName;
        }        public int memCacheSize = DEFAULT_MEM_CACHE_SIZE;
        public ImageCacheParams(File cachePath, String uniqueName) {
            this.cachePath = cachePath;
            this.uniqueName = uniqueName;
        }        public int diskCacheSize = DEFAULT_DISK_CACHE_SIZE;
        public CompressFormat compressFormat = DEFAULT_COMPRESS_FORMAT;
        public int compressQuality = DEFAULT_COMPRESS_QUALITY;
        public boolean memoryCacheEnabled = DEFAULT_MEM_CACHE_ENABLED;
        public boolean diskCacheEnabled = DEFAULT_DISK_CACHE_ENABLED;
        public boolean clearDiskCacheOnStart = DEFAULT_CLEAR_DISK_CACHE_ON_START;




    }
}