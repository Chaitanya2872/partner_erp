package com.iotiq.partnererp.common.web;

import com.iotiq.partnererp.common.constants.AppConstants;
import com.iotiq.partnererp.common.exception.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Builds a {@link Pageable} from request parameters, allowing only whitelisted sort properties (an unknown
 * property would otherwise surface as a server error).
 */
public final class Pageables {

    private Pageables() {
    }

    /**
     * @param sort "property" or "property,asc|desc"; null uses {@code defaultSort}
     */
    public static Pageable of(int page, int size, String sort, Set<String> allowed, Sort defaultSort) {
        if (page < 0) {
            throw ValidationException.of("page", "Page must be zero or greater.");
        }
        if (size < 1 || size > AppConstants.MAX_PAGE_SIZE) {
            throw ValidationException.of("size", "Size must be between 1 and " + AppConstants.MAX_PAGE_SIZE + ".");
        }
        Sort order = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = parts[0].trim();
            if (!allowed.contains(property)) {
                throw ValidationException.of("sort", "Sort by one of: " + String.join(", ", allowed) + ".");
            }
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            order = Sort.by(direction, property);
        }
        return PageRequest.of(page, size, order);
    }
}
