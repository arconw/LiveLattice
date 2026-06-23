package io.livelattice.importexport.service;

import io.livelattice.importexport.config.ImportExportProperties;
import io.livelattice.importexport.exception.ValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileValidator {

    private final ImportExportProperties properties;

    public FileValidator(ImportExportProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File is required");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new ValidationException("File exceeds maximum size of " + properties.maxFileSizeBytes() + " bytes");
        }
    }
}
