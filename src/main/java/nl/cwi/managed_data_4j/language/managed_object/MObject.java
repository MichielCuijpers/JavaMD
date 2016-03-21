package nl.cwi.managed_data_4j.language.managed_object;

import nl.cwi.managed_data_4j.language.data_manager.IFactory;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.MObjectField;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.errors.InvalidFieldValueException;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.errors.UnknownTypeException;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.many.MObjectFieldManyList;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.many.MObjectFieldManySet;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.single.MObjectFieldPrimitive;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.single.MObjectFieldRef;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.single.MObjectFieldSingle;
import nl.cwi.managed_data_4j.language.schema.models.definition.Field;
import nl.cwi.managed_data_4j.language.schema.models.definition.Klass;
import nl.cwi.managed_data_4j.language.schema.models.definition.M;
import nl.cwi.managed_data_4j.language.utils.PrimitiveUtils;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;

/**
 * The Managed Object
 * @author Theologos Zacharopoulos
 */
public class MObject implements InvocationHandler, M {

    // Store props for the object: <Name, Field>
    protected Map<String, MObjectField> props = new LinkedHashMap<>();

    // Keeps the types (schemaKlass pointer)
    protected Klass schemaKlass;

    // The data manager that manages this managed object.
    protected IFactory factory;

    /**
     * A managed object.
     *
     * @param schemaKlass the schema klass in which managed object belongs to
     * @param factory the data manager that manages this managed object.
     * @param initializers initialization values for the object.
     */
    public MObject(Klass schemaKlass, IFactory factory, Object... initializers) {
        this.schemaKlass = schemaKlass;
        this.factory = factory;

        if (this.schemaKlass.fields() != null) {

            // setup fields and properties / set default values.
            this.schemaKlass.fields().forEach(this::safeSetupField);

            // initialize fields with actual values.
            if (initializers != null) {
                this.safeInitializeProps(initializers);
            }
        }
    }

    /**
     * Wrapper to handle exceptions.
     */
    private void safeSetupField(Field _field) {
        try {
            this.setupField(_field);
        } catch (InvalidFieldValueException | UnknownTypeException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on field setup");
        }
    }

    /**
     * Create a MObjectField and put it in the object values, according to an input Field
     * @param field the input field.
     * @throws UnknownTypeException in case there is a weird primitive.
     * @throws InvalidFieldValueException in case of wrong value assignment to the field.
     */
    protected void setupField(Field field) throws UnknownTypeException, InvalidFieldValueException {

        if (field.type() == null) {
            throw new UnknownTypeException("Type of field '" + field.name() + "' is NULL");
        }

        if (!field.many()) {

            // if it is a primitive make it a Primitive field, otherwise a reference (managed object)
            if (PrimitiveUtils.isPrimitive(field.type().name())) {
                this.props.put(field.name(), new MObjectFieldPrimitive(this, field));
            } else {
                this.props.put(field.name(), new MObjectFieldRef(this, field));
            }
        } else {

            // if there is a key, then make it a Set, otherwise a list
            if (field.key() != null) {
                this.props.put(field.name(), new MObjectFieldManySet(this, field));
            } else {
                this.props.put(field.name(), new MObjectFieldManyList(this, field));
            }
        }
    }

    /**
     * Wrapper to handle exceptions.
     */
    private void safeInitializeProps(Object... initializers) {
        try {
            this.initializeProps(initializers);
        } catch (InvalidFieldValueException e) {
            e.printStackTrace();
            throw new RuntimeException("Error on field initialization");
        }
    }

    /**
     * Initializes the object values.
     * @param initializers the initialization values
     * @throws InvalidFieldValueException in case of wrong type of the initialization value.
     */
    protected void initializeProps(Object... initializers) throws InvalidFieldValueException {
        List<Field> fieldList = new LinkedList<>();
        fieldList.addAll(this.schemaKlass.fields());

        for (int i = 0; i < fieldList.size(); i++) {
            if (i < initializers.length) {
                final Field fld = fieldList.get(i);
                this._set(fld.name(), initializers[i]);
            }
        }
    }

    /**
     * Helper method that returns the MObject field by it's name.
     * Its used in order to avoid using reflection
     * to get the fields of a Managed Object, since it is already here.
     *
     * @param fieldName the field's name
     * @return the MObjectField from the managed Object properties
     */
    public MObjectField getMObjectField(String fieldName) {
        return this.props.get(fieldName);
    }

    /**
     * Extract the field and return its value
     * @param name the field name
     * @return the fields value.
     * @throws NoSuchFieldError in case there no field with this name.
     */
    protected Object _get(String name) throws NoSuchFieldError {
        final MObjectField mObjectField = this.props.get(name);

        // check if the field exists
        if (mObjectField == null) {
            throw new NoSuchFieldError(
                "No field named '" + name + "' in class '" + schemaKlass.name() + "'");
        }

        return mObjectField.get(); // return the field's value
    }

    /**
     * Sets the value of a existing field.
     * @param name the name of the field
     * @param value the value of the field
     * @throws NoSuchFieldError in case there no field with this name.
     * @throws InvalidFieldValueException in case the value is not the right type.
     */
    protected void _set(String name, Object value) throws NoSuchFieldError, InvalidFieldValueException {
        final MObjectField mObjectField = this.props.get(name);

        // check if the field exists
        if (mObjectField == null) {
            throw new NoSuchFieldError(
                "No field named'" + name + "' in class '" + schemaKlass.name() + "'");
        }

        // set the fields value
        final Field field = mObjectField.getField();
        if (field.many()) {

            // it's an array since it's many
            Object [] inits = ((Object[]) value);

            if (field.key() != null) {
                ((MObjectFieldManySet) mObjectField).init(new LinkedHashSet<>(Arrays.asList(inits)));
            } else {
                ((MObjectFieldManyList) mObjectField).init(new LinkedList<>(Arrays.asList(inits)));
            }

        } else {
            ((MObjectFieldSingle) mObjectField).init(value);
        }
    }

    /**
     * Searches for @key in the properties, if any found return the hashCode of the
     * first one it finds. Otherwise returns Java default hasCode() implementation.
     * @return a hashCode
     */
    private int getKeyHashCode() {
        for (MObjectField mObjectField : this.props.values()) {
            final Field field = mObjectField.getField();

            // TODO: Compute the right hashCode
            // if there is key at the fields type klass, use this one
            if (field.type().key() != null) {
                final MObjectField keyField = this.props.get(field.type().key().name());
                final Object theKey = keyField.get();

                final int keyHashCode = theKey.hashCode();

                return super.hashCode() * keyHashCode;
            }
        }

        // otherwise return Java hashCode implementation.
        return super.hashCode();
    }

    /**
     * The default method are forwarded to the InvocationHandler.
     * But we want to call the default implementation in case of existence.
     *
     * Check if the object has defined any default methods in its schema
     * The default method has already been overridden by the proxy and it can't be invoked directly.
     * In this case we invoke the default method with the given args.
     */
    private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
        final Class<?> declaringClass = method.getDeclaringClass();

        // declare MethodHandles.Lookup constructor accessible
        Constructor constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);

        // use the constructor to create a lookup object with PRIVATE access
        constructor.setAccessible(true);

        // create a lookup for the default method
        final MethodHandles.Lookup defaultMethodLookup =
                (MethodHandles.Lookup) constructor.newInstance(declaringClass, MethodHandles.Lookup.PRIVATE);

        // create a method handle that won’t check for overridden method (unreflectSpecial)
        // Since it is "special" it will skip the overriding done
        // by the proxying and invoke the default implementation
        return defaultMethodLookup
            .unreflectSpecial(method, declaringClass)
            .bindTo(proxy)
            .invokeWithArguments(args);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String fieldName = method.getName();

        // hashCode() method invocation of the proxied object
        if (method.getName().equals("hashCode")) {
            return this.getKeyHashCode();
        }

        // if the method is default, invoke this one
        if (method.isDefault()) {
            return invokeDefaultMethod(proxy, method, args);
        }

        // This is a way to execute the "attached" methods of the derived Managed Objects,
        // from the proxied objects. (e.g. point.observe()).
        //
        // In case there is already the method declared
        // (in one of the sub-classes/sub managedObjects),
        // then invoke it dynamically, and return.
        for (Method declaredMethod : this.getClass().getMethods()) {
            if (declaredMethod.getName().equals(fieldName)) {
                method.invoke(this, args);
                return null;
            }
        }

        // ================
        // Managed Object

        // if no args given, then just return the field's value.
        if (args == null) {
            // If is not an assignment, get the value.
            return _get(fieldName); // return the field's value
        }

        boolean isAssignment = false;

        Object fieldArgs = args[0];

        // If there are arguments, then it is considered as assignment.
        if (fieldArgs.getClass().isArray() && ((Object [])fieldArgs).length > 0) {
            isAssignment = true;
        }

        // If it is an assignment, then set the value to the field
        if (isAssignment) {

            final MObjectField mObjectField = this.props.get(fieldName);
            final boolean isMany = mObjectField.getField().many();

            // if it has one (1) argument, then means that it is a single field
            // At the same time, check always if the field is not many, for safety
            if (((Object [])fieldArgs).length == 1 && !isMany) {
                _set(fieldName, ((Object [])fieldArgs)[0]);
            } else {
                _set(fieldName, fieldArgs);
            }

            return null;
        }

        // If it is not an assignment, then just return the field's value.
        return _get(fieldName);
    }

    @Override
    public Klass schemaKlass(Klass... schemaKlass) {
        if (schemaKlass.length > 0) {
            this.schemaKlass = schemaKlass[0];
        }
        return this.schemaKlass;
    }
}