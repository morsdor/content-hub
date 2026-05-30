package com.contenthub.media.adapter.out.persistence;

import com.contenthub.media.domain.model.MediaStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MediaStatusConverter implements AttributeConverter<MediaStatus, String> {

    @Override
    public String convertToDatabaseColumn(MediaStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public MediaStatus convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : MediaStatus.valueOf(dbValue.toUpperCase());
    }
}
