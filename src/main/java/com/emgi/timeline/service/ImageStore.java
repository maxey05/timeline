package com.emgi.timeline.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns the folder that pasted and inserted pictures are copied into.
 *
 * <p>V1 decided that media would be <em>referenced</em> by address rather than copied.
 * A clipboard image has no address to reference -- the bytes exist only in the clipboard
 * -- so paste forces a copy, and once one path copies it is worse to have the other path
 * not copy: a picture that survives its source being moved and one that does not would
 * look identical on screen. So everything is copied, here, under a generated name.
 *
 * <p>Deliberately framework-free and deliberately not a repository: it writes loose files
 * beside the database rather than rows inside it. Nothing ever deletes from this folder,
 * which is the known cost -- removing an image from a description orphans its file.
 */
public final class ImageStore
{
    public static final String DEFAULT_EXTENSION = "png";

    /** Guards against a source file whose "extension" is really part of an attack. */
    private static final Pattern SAFE_EXTENSION = Pattern.compile("[a-z0-9]{1,8}");

    private final Path directory;

    public ImageStore(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath();
    }

    public static ImageStore atDefaultLocation()
    {
        return new ImageStore(defaultImageDirectory());
    }

    public static Path defaultImageDirectory()
    {
        return Path.of(System.getProperty("user.home"), ".timeline", "images");
    }

    public Path directory()
    {
        return directory;
    }

    /** Writes {@code bytes} under a generated name and returns the address to store. */
    public URI store(byte[] bytes, String extension)
    {
        Objects.requireNonNull(bytes, "bytes");

        Path target = directory.resolve(UUID.randomUUID() + "." + safeExtension(extension));

        try
        {
            Files.createDirectories(directory);
            Files.write(target, bytes);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Could not save the image to " + target, e);
        }

        return target.toUri();
    }

    /** Copies an existing file in, keeping its extension but not its name. */
    public URI copyFrom(Path source)
    {
        Objects.requireNonNull(source, "source");

        Path target = directory.resolve(UUID.randomUUID() + "." + extensionOf(source));

        try
        {
            Files.createDirectories(directory);
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException("Could not copy " + source + " into " + directory, e);
        }

        return target.toUri();
    }

    private static String extensionOf(Path source)
    {
        String name = source.getFileName() == null ? "" : source.getFileName().toString();
        int dot = name.lastIndexOf('.');

        return safeExtension(dot < 0 ? null : name.substring(dot + 1));
    }

    private static String safeExtension(String raw)
    {
        if(raw == null)
        {
            return DEFAULT_EXTENSION;
        }

        String lower = raw.strip().toLowerCase(Locale.ROOT);
        return SAFE_EXTENSION.matcher(lower).matches() ? lower : DEFAULT_EXTENSION;
    }
}
