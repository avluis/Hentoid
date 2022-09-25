package me.devsaki.hentoid.json;

import me.devsaki.hentoid.database.domains.RenamingRule;
import me.devsaki.hentoid.enums.AttributeType;

class JsonRenamingRule {

    private AttributeType type;
    private String sourceName;
    private String targetName;

    private JsonRenamingRule() {
    }

    static JsonRenamingRule fromEntity(RenamingRule data) {
        JsonRenamingRule result = new JsonRenamingRule();
        result.type = data.getAttributeType();
        result.sourceName = data.getSourceName();
        result.targetName = data.getTargetName();
        return result;
    }

    RenamingRule toEntity() {
        return new RenamingRule(type, sourceName, targetName);
    }
}
