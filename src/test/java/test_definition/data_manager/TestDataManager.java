package test_definition.data_manager;

import nl.cwi.managed_data_4j.language.data_manager.BasicDataManager;
import nl.cwi.managed_data_4j.language.schema.models.definition.Klass;
import nl.cwi.managed_data_4j.language.schema.models.definition.Schema;

public class TestDataManager extends BasicDataManager {

    public TestDataManager(Class<?> moSchemaFactoryClass, Schema schema) {
        super(moSchemaFactoryClass, schema);
    }

    @Override
    protected TestMObject createManagedObject(Klass klass, Object... inits) {
        return new TestMObject(klass, inits);
    }
}
