package cm.imf.pipeline.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ResultatActionRecouvrementConverter implements AttributeConverter<ResultatActionRecouvrement, String> {
    @Override
    public String convertToDatabaseColumn(ResultatActionRecouvrement attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public ResultatActionRecouvrement convertToEntityAttribute(String dbData) {
        try {
            return RecouvrementEnumCodes.resultat(dbData);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
