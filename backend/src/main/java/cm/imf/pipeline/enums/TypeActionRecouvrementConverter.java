package cm.imf.pipeline.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TypeActionRecouvrementConverter implements AttributeConverter<TypeActionRecouvrement, String> {
    @Override
    public String convertToDatabaseColumn(TypeActionRecouvrement attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TypeActionRecouvrement convertToEntityAttribute(String dbData) {
        try {
            return RecouvrementEnumCodes.typeAction(dbData);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
