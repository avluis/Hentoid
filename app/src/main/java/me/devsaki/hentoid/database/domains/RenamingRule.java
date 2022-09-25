package me.devsaki.hentoid.database.domains;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

import io.objectbox.annotation.Convert;
import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;
import io.objectbox.annotation.Transient;
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

    @Transient
    String leftPart;
    @Transient
    String rightPart;


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

    public boolean doesMatchSourceName(@NonNull String name) {
        int starIndex = sourceName.indexOf('*');
        if (-1 == starIndex) return sourceName.equalsIgnoreCase(name);
        else {
            boolean result = true;
            String strLower = name.toLowerCase(Locale.ROOT);
            if (!leftPart.isEmpty()) result = strLower.startsWith(leftPart);
            if (!rightPart.isEmpty()) result &= strLower.endsWith(rightPart);
            return result;
        }
    }

    public String getTargetName(@NonNull String name) {
        if (!targetName.contains("*")) return targetName;

        int starIndex = sourceName.indexOf('*');
        if (-1 == starIndex) return targetName;
        else {
            String sourceWildcard = name;
            if (!leftPart.isEmpty()) sourceWildcard = sourceWildcard.substring(leftPart.length());
            if (!rightPart.isEmpty())
                sourceWildcard = sourceWildcard.substring(0, sourceWildcard.length() - rightPart.length() - 1);
            return targetName.replaceFirst("\\*", sourceWildcard);
        }
    }

    public void computeParts() {
        int starIndex = sourceName.indexOf('*');
        if (starIndex > -1) {
            leftPart = sourceName.substring(0, starIndex).toLowerCase(Locale.ROOT);
            rightPart = (starIndex < sourceName.length() - 1) ? sourceName.substring(starIndex + 1, sourceName.length() - 1).toLowerCase(Locale.ROOT) : "";
        }
    }

    @NonNull
    @Override
    public String toString() {
        return attributeType + ":" + sourceName + "=>" + targetName;
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
