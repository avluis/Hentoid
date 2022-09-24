package me.devsaki.hentoid.database.domains;

import java.util.Objects;

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

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenamingRule that = (RenamingRule) o;
        return attributeType.equals(that.attributeType) && Objects.equals(sourceName, that.sourceName) && Objects.equals(targetName, that.targetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributeType, sourceName, targetName);
    }
}
