package dev.alma.protobuf.serdes;

import com.google.protobuf.Descriptors.Descriptor;
import java.util.Map;

final class DescriptorSchema {

    private final Map<String, Descriptor> descriptorsByFullName;

    DescriptorSchema(Map<String, Descriptor> descriptorsByFullName) {
        this.descriptorsByFullName = Map.copyOf(descriptorsByFullName);
    }

    Descriptor messageDescriptor(String fullMessageName) {
        return descriptorsByFullName.get(fullMessageName);
    }
}
