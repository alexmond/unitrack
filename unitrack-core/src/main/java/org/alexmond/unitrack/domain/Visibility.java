package org.alexmond.unitrack.domain;

/**
 * Project visibility. {@code PUBLIC} projects are readable by anyone (including anonymous
 * users in open mode); {@code PRIVATE} projects are readable only by their members (READ
 * or higher) and global {@link Role#ADMIN} users.
 */
public enum Visibility {

	PUBLIC, PRIVATE

}
