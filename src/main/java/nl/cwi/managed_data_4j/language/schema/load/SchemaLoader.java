package nl.cwi.managed_data_4j.language.schema.load;

import nl.cwi.managed_data_4j.language.managed_object.MObject;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.errors.UnknownTypeException;
import nl.cwi.managed_data_4j.language.schema.boot.BootSchema;
import nl.cwi.managed_data_4j.language.schema.boot.SchemaFactory;
import nl.cwi.managed_data_4j.language.schema.models.definition.*;
import nl.cwi.managed_data_4j.language.schema.models.definition.annotations.Contain;
import nl.cwi.managed_data_4j.language.schema.models.definition.annotations.Inverse;
import nl.cwi.managed_data_4j.language.schema.models.definition.annotations.Key;
import nl.cwi.managed_data_4j.language.utils.ArrayUtils;
import nl.cwi.managed_data_4j.language.utils.ReflectionUtils;
import org.apache.log4j.LogManager;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamically loads schemas.
 * This is where the whole loading process is done,
 * given Schema definition (interface), this class provides an instance
 * of this schema.
 *
 * @author Theologos Zacharopoulos
 */
public class SchemaLoader {

    private static final org.apache.log4j.Logger logger = LogManager.getLogger(SchemaLoader.class.getName());

    /**
     * Helper private class to keep Fields with their Method which define them.
     */
    private static class FieldWithMethod {

        public Field field = null;
        public Method method = null;

        public FieldWithMethod(Field field, Method method) {
            this.field = field;
            this.method = method;
        }
    }

    /**
     * Helper private class to keep Types with their Classes which define them.
     */
    private static class TypeWithClass {

        public Type type = null;
        public Class<?> clazz = null;

        public TypeWithClass(Type type, Class<?> clazz) {
            this.type = type;
            this.clazz = clazz;
        }
    }

    // helper cache for the loading process.
    private static Map<String, Type> typesCache = new LinkedHashMap<>();

    /**
     * Bootstraps the schema schema
     * @return The instance of SchemaSchema
     */
    public static Schema bootLoad() {
        return new BootSchema();
    }

    /**
     * Convert from a schema definitions (interface) to an instance of Schema.
     * @param factory the factory which creates the schema
     * @param schemaKlassesDef the schemas definitions (interfaces) to be converted.
     * @return the instance of Schema
     */
    public static Schema load(SchemaFactory factory, Class<?>... schemaKlassesDef) {

        logger.debug("Make schema form SchemaFactory");

        // create an empty schema using the factory, will wire it later
        final Schema schema = factory.schema();

        // build the types from the schema klasses definition
        final Set<Type> types = buildTypesFromClasses(factory, schema, schemaKlassesDef);

        // wire the types on schema
        schema.types(types.toArray(new Type[types.size()]));

        // Get the invocation handler of the Managed Object,
        // in order to get the schemaKlass from it.
        // Since it has been already set from the factory.
        final MObject schemaManagedObject = (MObject) Proxy.getInvocationHandler(schema);

        // get the schema klass
        final Klass schemaKlass = schemaManagedObject.schemaKlass();

        // set the schema klass
        schema.schemaKlass(schemaKlass);

        return schema;
    }

    public static Set<Type> buildTypesFromClasses(
            SchemaFactory factory,
            Schema schema,
            Class<?>... schemaKlassesDefinition)
    {
        Map<Type, TypeWithClass> types = new LinkedHashMap<>();
        Map<Field, FieldWithMethod> allFieldsWithReturnType = new LinkedHashMap<>();

        // =================
        // Klasses
        for (Class<?> schemaKlassDefinition : schemaKlassesDefinition) {
            final String klassName = schemaKlassDefinition.getSimpleName();

            logger.debug("*Build: " + klassName + " klass");

            final Map<String, Field> fieldsForKlass =
                buildFieldsFromMethods(factory, schemaKlassDefinition, allFieldsWithReturnType);

            // create a new klass
            final Klass klass = factory.klass();
            klass.name(klassName);
            klass.schema(schema);
            klass.fields(fieldsForKlass.values().toArray(new Field[fieldsForKlass.values().size()]));

            // wire the owner klass in fields
            for (Field field : fieldsForKlass.values()) {
                field.owner(klass);
            }

            typesCache.put(klass.name(), klass);

            // add the a new klass
            types.put(klass, new TypeWithClass(klass, schemaKlassDefinition));
        }

        wireFieldTypes(factory, schema, allFieldsWithReturnType);
        wireFieldTypeKeys(allFieldsWithReturnType);
        wireFieldInverse(allFieldsWithReturnType);

        wireKlassSupers(types, typesCache);
        wireKlassSubs(types, typesCache);
        wireKlassClassOf(types, schemaKlassesDefinition);

        typesCache.clear();
        return types.keySet();
    }

    private static void wireFieldTypeKeys(Map<Field, FieldWithMethod> allFieldsWithReturnType) {
        allFieldsWithReturnType.keySet().stream()
            .filter(Field::many)
            .map(Field::type)
            .filter(Klass.class::isInstance)
            .map(Klass.class::cast)
            .forEach(fieldTypeKlass ->
                fieldTypeKlass.key(
                    fieldTypeKlass.fields().stream()
                        .filter(Field::key)
                        .findFirst()
                        .orElse(null))
        );
    }

    private static void wireFieldTypes(
        SchemaFactory factory,
        Schema schema,
        Map<Field, FieldWithMethod> allFieldsWithReturnType)
    {
        for (Field field : allFieldsWithReturnType.keySet()) {
            final Method method = allFieldsWithReturnType.get(field).method;

            // In case the field is multi value, that means that the real type is
            // not given in the method.getReturnType() because this will give Set ot List,
            // BUT the real type is in method.getGenericReturnType().
            if (field.many()) {
                final ParameterizedType fieldManyType = (ParameterizedType) method.getGenericReturnType();
                final Class<?> fieldManyTypeClass = (Class<?>) fieldManyType.getActualTypeArguments()[0];

                final Type fieldType = getFieldType(fieldManyTypeClass, schema, factory);
                field.type(fieldType);

            } else {
                final Class<?> fieldSingleTypeClass = method.getReturnType();
                final Type fieldType = getFieldType(fieldSingleTypeClass, schema, factory);
                field.type(fieldType);
            }
        }
    }

    private static void wireFieldInverse(Map<Field, FieldWithMethod> allFieldsWithReturnType) {
        for (Field field : allFieldsWithReturnType.keySet()) {
            final Method method = allFieldsWithReturnType.get(field).method;

            final Field fieldInverseField = buildInverse(method, allFieldsWithReturnType);
            if (fieldInverseField != null) {
                field.inverse(fieldInverseField);
            }
        }
    }

    private static void wireKlassSupers(Map<Type, TypeWithClass> types, Map<String, Type> cache) {
        types.keySet().stream()
            .filter(Klass.class::isInstance)
            .map(Klass.class::cast)
            .forEach(klass -> {
                final TypeWithClass typeWithClass = types.get(klass);
                final Set<Klass> superKlasses = buildSupers(typeWithClass.clazz, cache);
                if (superKlasses.size() > 0) {
                    klass.supers(superKlasses.toArray(new Klass[superKlasses.size()]));
                }
            });
    }

    private static void wireKlassSubs(Map<Type, TypeWithClass> types, Map<String, Type> cache) {
        types.keySet().stream()
            .filter(Klass.class::isInstance)
            .map(Klass.class::cast)
            .forEach(klass -> {
                final TypeWithClass typeWithClass = types.get(klass);
                final Set<Klass> subKlasses = buildSubs(typeWithClass.clazz, cache);
                if (subKlasses.size() > 0) {
                    final Klass[] subsToArray = subKlasses.toArray(new Klass[subKlasses.size()]);
                    klass.subklasses(subsToArray);
                }
            });
    }

    public static void wireKlassClassOf(Map<Type, TypeWithClass> types, Class<?>[] schemaKlassesDefinition) {
        for (Type type : types.keySet()) {
            for (Class klassInterface : schemaKlassesDefinition) {
                if (type instanceof Klass) {
                    if (klassInterface.getSimpleName().equals(type.name())) {
                        ((Klass) type).classOf(klassInterface);
                    }
                }
            }
        }
    }

    public static Map<String, Field> buildFieldsFromMethods(
            SchemaFactory factory,
            Class<?> schemaKlassDefinition,
            Map<Field, FieldWithMethod> allFieldsWithReturnType)
    {
        Map<String, Field> fieldsForKlass = new LinkedHashMap<>();

        // ** Issue #1 **
        // The elements in the array returned getMethods(), are not sorted and are not in any particular order.
        // Looks like it is not possible to get them in the definition order, by using reflection.
        // So for now, the order of the methods are alphabetical, and it takes only the primitives in the ordering.
        // That means that a constructor with initialization in the schema factory, should have order arguments
        // in alphabetical order, and include only the primitives.
        final Method[] fields = schemaKlassDefinition.getMethods();
        Arrays.sort(fields, ReflectionUtils.methodsOrderComparator());

        for (Method schemaKlassField : fields) {
            final String fieldName = schemaKlassField.getName();
            final Class<?> fieldReturnClass = schemaKlassField.getReturnType();

            logger.debug(" - Build: " + fieldName + " <" + fieldReturnClass.getSimpleName() + ">" + " field");

            // check for many
            final boolean many = ArrayUtils.isMany(fieldReturnClass);

            // check for optional
            final boolean optional = schemaKlassField.isAnnotationPresent(nl.cwi.managed_data_4j.language.schema.models.definition.annotations.Optional.class);

            // check for key
            final boolean key = schemaKlassField.isAnnotationPresent(Key.class);

            // check for contain
            final boolean contain = schemaKlassField.isAnnotationPresent(Contain.class);

            // add its fields, the owner Klass will be added later
            final Field field = factory.field(contain, key, many, fieldName, optional);

            fieldsForKlass.put(fieldName, field);
            allFieldsWithReturnType.put(field, new FieldWithMethod(field, schemaKlassField));
        }
        return fieldsForKlass;
    }

    private static Field buildInverse(Method method, Map<Field, FieldWithMethod> allFieldsWithReturnType) {
        Field fieldInverseField = null;
        if (method.isAnnotationPresent(Inverse.class)) {
            final Inverse fieldInverse = method.getAnnotation(Inverse.class);

            // get the inverse-other klass
            final Class<?> inverseOther = fieldInverse.other();
            final String inverseOtherName = inverseOther.getSimpleName();

            // get the inverse-field
            final String fieldInverseFieldName = fieldInverse.field();

            // check field name and owner
            for (Field fieldForSearch : allFieldsWithReturnType.keySet()) {
                final String fieldForSearchName = fieldForSearch.name();
                final String fieldForSearchOwnerName = fieldForSearch.owner().name();

                if  (fieldForSearchName.equals(fieldInverseFieldName) &&
                     fieldForSearchOwnerName.equals(inverseOtherName))
                {
                    fieldInverseField = fieldForSearch;
                }
            }
        }
        return fieldInverseField;
    }

    private static Type getFieldType(Class<?> fieldReturnClass, Schema schema, SchemaFactory factory) {
        try {
            return TypeFactory.getTypeFromClass(fieldReturnClass, schema, factory, typesCache);
        } catch (UnknownTypeException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static Set<Klass> buildSupers(Class<?> schemaKlassDefinition, Map<String, Type> cache) {
        Set<Klass> supers = new LinkedHashSet<>();

        // Add interfaces that implements
        for (Class<?> interfaceImpl : schemaKlassDefinition.getInterfaces()) {
            final Type superKlass = cache.get(interfaceImpl.getSimpleName());
            supers.add((Klass) superKlass);
        }

        // Add superclass if any.
        if (schemaKlassDefinition.getSuperclass() != null) {
            final Type superKlass = cache.get(schemaKlassDefinition.getSuperclass().getSimpleName());
            supers.add((Klass) superKlass);
        }

        return supers.isEmpty() ? Collections.emptySet() : supers;
    }

    private static Set<Klass> buildSubs(Class<?> schemaKlassDefinition, Map<String, Type> cache) {
        Set<Klass> subs = cache.values().stream()
            .filter(Klass.class::isInstance)
            .map(Klass.class::cast)
            .flatMap(klass -> klass.supers().stream())
            .filter(Objects::nonNull)
            .filter(superKlass -> superKlass.name() != null)
            .filter(superKlass -> superKlass.name().equals(schemaKlassDefinition.getSimpleName()))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        return subs.isEmpty() ? Collections.emptySet() : subs;
    }
}