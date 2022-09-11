package me.devsaki.hentoid.database.domains;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import me.devsaki.hentoid.enums.AttributeType;

@Entity
public class RenamingRule {

    @Id
    public long id;
    @Index
    @Convert(converter = AttributeType.AttributeTypeConverter.class, dbType = Integer.class)
    private AttributeType attributeType;
    @Index
    private String sourceName;
    private String targetName;

    public RenamingRule() { // Required by ObjectBox when an alternate constructor exists
    }

    public RenamingRule(AttributeType type, String sourceName, String targetName) {
        attributeType = type;
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    public AttributeType getAttributeType() {
        return attributeType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTargetName() {
        return targetName;
    }
}
