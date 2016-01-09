package nl.cwi.managed_data_4j.schema.load.utils;

import nl.cwi.managed_data_4j.schema.load.models.NullFieldImpl;
import nl.cwi.managed_data_4j.schema.load.models.NullTypeImpl;
import nl.cwi.managed_data_4j.schema.models.schema_schema.Field;
import nl.cwi.managed_data_4j.schema.models.schema_schema.Type;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton Class which keeps a cache of types and fields
 * in order not to re-create them on the schema loading process.
 */
public class SchemaLoaderCache {

    private static SchemaLoaderCache instance = null;

    private Map<String, Type> typesCache = new HashMap<>();
    private Map<String, Field> fieldsCache = new HashMap<>();

    public static SchemaLoaderCache getInstance() {
        if (instance == null) {
            instance = new SchemaLoaderCache();
        }
        return instance;
    }

    public Type getType(String typeName) {
        if (!typesCache.containsKey(typeName)) {
            return new NullTypeImpl();
        }
        return typesCache.get(typeName);
    }

    public void addType(String typeName, Type type) {
        typesCache.put(typeName, type);
    }

    public Field getField(String fieldName) {
        if (!fieldsCache.containsKey(fieldName)) {
            return new NullFieldImpl();
        }
        return fieldsCache.get(fieldName);
    }

    public void addField(String fieldName, Field field) {
        fieldsCache.put(fieldName, field);
    }

    public Collection<Field> getAllFields() {
        return fieldsCache.values();
    }

    public Collection<Type> getAllTypes() {
        return typesCache.values();
    }

    public void clean() {
        fieldsCache.clear();
        typesCache.clear();
    }
}
