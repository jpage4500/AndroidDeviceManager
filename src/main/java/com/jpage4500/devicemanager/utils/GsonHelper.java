package com.jpage4500.devicemanager.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.internal.Primitives;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class GsonHelper {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GsonHelper.class);
    private static volatile Gson instance;

    private static Gson getInstance() {
        if (instance == null) {
            synchronized (GsonHelper.class) {
                if (instance == null) {
                    instance = newInstance();
                }
            }
        }
        return instance;
    }

    /**
     * create new instance of Gson
     */
    public static Gson newInstance() {
        GsonBuilder gsonBuilder = gsonBuilder();
        return gsonBuilder.create();
    }

    public static GsonBuilder gsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        // NOTE: I don't like this but need it to compare restored devices with list response
        //gsonBuilder.serializeNulls();
        gsonBuilder.setLenient();
        return gsonBuilder;
    }

    /**
     * convert JSON string into a List of type T
     * NOTE: does NOT return null
     */
    public static <T> List<T> stringToList(String string, Class<T> classOfT) {
        if (TextUtils.notEmpty(string)) {
            try {
                ListType<T> list = new ListType<>(classOfT);
                return getInstance().fromJson(string, list);
            } catch (Exception e) {
                //log.error("stringToList: JsonSyntaxException: {}, {}", string, e.getMessage());
                log.error("stringToList: JsonSyntaxException: {}, {}", string, e.getMessage());
            }
        }
        // create new list
        return new ArrayList<>();
    }

    /**
     * convert JSON string into a List of type T
     * NOTE: does NOT return null
     */
    public static <T> Set<T> stringToSet(String string, Class<T> classOfT) {
        if (TextUtils.notEmpty(string)) {
            try {
                SetType<T> list = new SetType<>(classOfT);
                return getInstance().fromJson(string, list);
            } catch (Exception e) {
                log.error("stringToList: JsonSyntaxException: {}, {}", string, e.getMessage());
            }
        }
        // create new set
        return new HashSet<>();
    }

    /**
     * convert JSON string into a Map of type T,K
     * NOTE: does NOT return null
     */
    public static <T, K> Map<T, K> stringToMap(String string, Class<T> keyClass, Class<K> valueClass) {
        if (TextUtils.notEmpty(string)) {
            try {
                MapType<T, K> list = new MapType<>(keyClass, valueClass);
                return getInstance().fromJson(string, list);
            } catch (Exception e) {
                log.error("stringToMap: JsonSyntaxException: {}, {}", string, e.getMessage());
            }
        }
        // create new list
        return new HashMap<>();
    }

    /**
     * @return Object of class T, re-created from JSON string
     * NOTE: **can** be null
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        if (json == null || json.length() == 0) return null;
        try {
            Object object = getInstance().fromJson(json, (Type) classOfT);
            return Primitives.wrap(classOfT).cast(object);
        } catch (Exception e) {
            log.error("fromJson: {}, {}", json, e.getMessage());
            return null;
        }
    }

    /**
     * @return JSON encoded string of Object - never null
     */
    public static String toJson(Object src) {
        return getInstance().toJson(src);
    }

    /**
     * required for de-serializing a List containing type T objects
     */
    private static class ListType<T> implements ParameterizedType {
        private Class<T> elementsClass;

        ListType(Class<T> elementsClass) {
            this.elementsClass = elementsClass;
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{elementsClass};
        }

        public Type getRawType() {
            return List.class;
        }

        public Type getOwnerType() {
            return null;
        }
    }

    /**
     * required for de-serializing a Set containing type T objects
     */
    private static class SetType<T> implements ParameterizedType {
        private Class<T> elementsClass;

        SetType(Class<T> elementsClass) {
            this.elementsClass = elementsClass;
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{elementsClass};
        }

        public Type getRawType() {
            return Set.class;
        }

        public Type getOwnerType() {
            return null;
        }
    }

    /**
     * required for de-serializing a Map containing key type T and value type K objects
     */
    private static class MapType<T, K> implements ParameterizedType {
        private Class<T> keyClass;
        private Class<K> valueClass;

        MapType(Class<T> keyClass, Class<K> valueClass) {
            this.keyClass = keyClass;
            this.valueClass = valueClass;
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{keyClass, valueClass};
        }

        public Type getRawType() {
            return Map.class;
        }

        public Type getOwnerType() {
            return null;
        }
    }

}
