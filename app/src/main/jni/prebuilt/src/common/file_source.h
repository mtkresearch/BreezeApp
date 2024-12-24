#pragma once

#include "file_mem_mapper.h"

#include <ostream>
#include <string>
#include <string_view>

// A file path or buffer wrapper, which is initialized with either a file path or buffer,
// but only allows accessing the file data directly without its path.
//
// If it is initialized with a file path, then the FileSource instance owns and manages the
// lifecycle of the file buffer, and the file will be lazy loaded only when needed unless load() is
// explicitly called.
//
// However, if it is initialized with user defined file buffer, the FileSource instance does not
// manage the lifecycle of the buffer.
//
// NOTE: It is recommeded to pass FileSource by value so that the reference counter in shared_ptr
// works together with RAII, so that the underlying object is destroyed when no one is using it.
class FileSource {
public:
    // Empty file source
    FileSource() {}

    // File source with path
    FileSource(const std::string& path) : mPath(path) {}

    // File source directly with buffer, with optional name as description
    FileSource(const void* data, const size_t size, const std::string& name = "")
        : mName(name), mFileData((const char*)data, size) {}

    FileSource& operator=(const FileSource& other) {
        mPath = other.mPath;
        mName = other.mName;
        mFileData = other.mFileData;
        mFileMemMapper = other.mFileMemMapper;
        return *this;
    }

    // Check if the file source is used, aka not empty
    explicit operator bool() const;

    // Check if the file source is empty
    bool empty() const;

    // Return the path if possible, otherwise return the given name.
    const std::string& getName() const;

    // Get the file buffer. Will load the file if not yet loaded.
    const char* getData() const;

    // Get the file size. Will load the file if not yet loaded.
    size_t getSize() const;

    // Get the file buffer and its size in bytes. Will load the file if not yet loaded.
    std::pair<const char*, size_t> get() const;

    // Returns whether the file can be read successfully
    bool valid() const;

    // Check if the FileSource instance owns and manages the buffer lifecycle
    bool hasBufferOwnership() const;

    // Load the file if not yet loaded if it has path given.
    // Returns true if file is loaded successfully, false if otherwise.
    bool load();

    // Hint for file release to indicate that this instance has done reading the file.
    // Returns true if releasable, false if otherwise.
    // Note that the file will only be released when the last FileMemMapper shared_ptr object that
    // owns the file has been destroyed.
    bool hint_release() const;

private:
    const std::string_view& getFileData() const;

    void releaseFileData() const;

private:
    std::string mPath;
    std::string mName;
    mutable std::string_view mFileData;
    mutable std::shared_ptr<FileMemMapper> mFileMemMapper;
};

// Support FileSource in ostream
std::ostream& operator<<(std::ostream& stream, const FileSource& fileSource);