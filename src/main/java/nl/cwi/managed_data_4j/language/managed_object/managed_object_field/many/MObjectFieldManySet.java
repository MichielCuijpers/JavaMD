package nl.cwi.managed_data_4j.language.managed_object.managed_object_field.many;

import nl.cwi.managed_data_4j.language.managed_object.MObject;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.errors.InvalidFieldValueException;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.errors.NoKeyFieldException;
import nl.cwi.managed_data_4j.language.managed_object.managed_object_field.errors.UnknownTypeException;
import nl.cwi.managed_data_4j.language.schema.models.definition.Field;
import nl.cwi.managed_data_4j.language.schema.models.definition.M;
import nl.cwi.managed_data_4j.language.utils.ReflectionUtils;

import java.util.*;

/**
 * Represents a multi value field which is a Set.
 * @author Theologos Zacharopoulos
 */
public class MObjectFieldManySet extends MObjectFieldMany {

    protected Map<Object, Object> values;

    public MObjectFieldManySet(MObject owner, Field field) throws UnknownTypeException {
        super(owner, field);
        this.values = defaultValue();
    }

    @Override
    public void init(Object values) throws InvalidFieldValueException, NoKeyFieldException  {
        super.init(values);

        // it's an array since it's many
        for (Object initValue : ((Collection) values)) {
            this.add(initValue);
        }
    }

    @Override
    protected Map<Object, Object> defaultValue() throws UnknownTypeException {
        return new LinkedHashMap<>();
    }

    @Override
    public Iterator iterator() {
        return this.values.values().iterator();
    }

    @Override
    public Object get() {
        return new LinkedHashSet<>(this.values.values());
    }

    @Override
    public void add(Object value) throws NoKeyFieldException {
        if (value == null) return;

        // since we do not support (yet) Sets of primitives,
        // this would be a managed object.
        final M mObjectNewValue = (M)value;
        final Field newValueKeyField = mObjectNewValue.schemaKlass().key();

        if (newValueKeyField == null) {
            throw new NoKeyFieldException("No key when adding " + mObjectNewValue + " to #{self}");
        }

        final Object newValueKeyValue =
                ReflectionUtils.getValueFromFieldSafe(value, newValueKeyField.name(), newValueKeyField.type().classOf());

        if (this.values.get(newValueKeyValue) != value) {
            __insert(value);
        }
    }

    @Override
    public void __insert(Object value) throws NoKeyFieldException {
        // since we do not support (yet) Sets of primitives,
        // this would be a managed object.
        final M mObjectNewValue = (M)value;
        final Field newValueKeyField = mObjectNewValue.schemaKlass().key();

        if (newValueKeyField == null) {
            throw new NoKeyFieldException("No key when adding " + mObjectNewValue + " to #{self}");
        }

        final Object newValueKeyValue =
                ReflectionUtils.getValueFromFieldSafe(value, newValueKeyField.name(), newValueKeyField.type().classOf());

        this.values.put(newValueKeyValue, value);
    }

    @Override
    public void __delete(Object value) throws NoKeyFieldException {
        // since we do not support (yet) Sets of primitives,
        // this would be a managed object.
        final M mObjectNewValue = (M)value;
        final Field newValueKeyField = mObjectNewValue.schemaKlass().key();

        if (newValueKeyField == null) {
            throw new NoKeyFieldException("No key when adding " + mObjectNewValue + " to #{self}");
        }

        final Object newValueKeyValue =
                ReflectionUtils.getValueFromFieldSafe(value, newValueKeyField.name(), newValueKeyField.type().classOf());

        this.values.remove(newValueKeyValue);
    }

//    private Optional<Object> findValue(Object value) {
//
//        // since we do not support (yet) Sets of primitives,
//        // this would be a managed object.
//        final M mObjectNewValue = (M)value;
//        final Field newValueKeyField = mObjectNewValue.schemaKlass().key();
//
//        // in order to insert values in the set, we need to check first if
//        // the value already exists, Java's Set ca not do that since the objects are proxied
//        // so this is the place that we need to use the, in order to check for duplicates
//        if (newValueKeyField != null) {
//            for (Object existingValue : this.value) {
//                final Field existingValueKeyField = ((M)existingValue).schemaKlass().key();
//                if (existingValueKeyField != null) {
//
//                    final Object existingValueKeyValue =
//                            ReflectionUtils.getValueFromFieldSafe(existingValue, existingValueKeyField.name(), existingValueKeyField.type().classOf());
//                    final Object newValueKeyValue =
//                            ReflectionUtils.getValueFromFieldSafe(value, newValueKeyField.name(), newValueKeyField.type().classOf());
//
//                    if (newValueKeyValue != null && existingValueKeyValue != null &&
//                            existingValueKeyValue.equals(newValueKeyValue))
//                    {
//                        return Optional.of(existingValue);
//                    }
//                }
//            }
//        }
//        return Optional.empty();
//    }
}
