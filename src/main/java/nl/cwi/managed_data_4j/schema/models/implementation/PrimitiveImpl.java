package nl.cwi.managed_data_4j.schema.models.implementation;

import nl.cwi.managed_data_4j.schema.models.definition.Field;
import nl.cwi.managed_data_4j.schema.models.definition.Primitive;
import nl.cwi.managed_data_4j.schema.models.definition.Schema;

/**
 * Represents an implementation of a Primitive Schema definition.
 * @author Theologos Zacharopoulos
 */
public class PrimitiveImpl implements Primitive {

    protected String name = null;
    protected Schema schema = null;
    protected Field key = null;

    public PrimitiveImpl(String name) {
        this(name, null);
    }

    public PrimitiveImpl(String name, Schema schema) {
        this.name = name;
        this.schema = schema;
    }

    @Override
    public String name(String... name) {
        if (name.length > 0) {
            this.name = name[0];
        }
        return this.name;
    }

    @Override
    public Schema schema(Schema... schema) {
        if (schema.length > 0) {
            this.schema = schema[0];
        }
        return this.schema;
    }

    @Override
    public Field key(Field... key) {
        if (key.length > 0) {
            this.key = key[0];
        }
        return this.key;
    }
}
