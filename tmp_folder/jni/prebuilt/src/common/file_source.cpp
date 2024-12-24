#include "file_source.h"

#include "logging.h"

#include <ostream>
#include <string>
#include <string_view>

// Check if the file source is used, aka not empty
FileSource::operator bool() const {
    return !empty();
}

// Check if the file source is empty
bool FileSource::empty() const {
    return (mPath.empty() && mFileData.empty());
}

// Return the path if possible, otherwise return the given name.
const std::string& FileSource::getName() const {
    static const std::string unnamed = "Unnamed";
    if (!mPath.empty())
        return mPath;
    if (mName.empty())
        return unnamed;
    return mName;
}

const char* FileSource::getData() const {
    if (!valid()) {
        LOG(WARN) << "Unable to load " << *this;
    }
    return getFileData().data();
}

size_t FileSource::getSize() const {
    if (!valid()) {
        LOG(WARN) << "Unable to load " << *this;
    }
    return getFileData().size();
}

std::pair<const char*, size_t> FileSource::get() const {
    if (!valid()) {
        LOG(WARN) << "Unable to load " << *this;
    }
    const auto& fileData = getFileData();
    return {fileData.data(), fileData.size()};
}

// Returns whether the file can be read successfully
bool FileSource::valid() const {
    return !getFileData().empty();
}

// Check if the FileSource instance owns and manages the buffer lifecycle
bool FileSource::hasBufferOwnership() const {
    return !mPath.empty();
}

// Load the file if not yet loaded and has path given.
// Returns true if file is loaded successfully, false if otherwise.
bool FileSource::load() {
    if (!mFileData.empty() || mPath.empty()) {
        return true;
    }
    DCHECK(!mFileMemMapper);
    mFileMemMapper = std::make_shared<FileMemMapper>(mPath);
    if (mFileMemMapper->valid()) {
        const auto [data, size] = mFileMemMapper->get();
        mFileData = std::string_view(data, size);
    }
    return mFileMemMapper->valid();
}

bool FileSource::hint_release() const {
    if (!hasBufferOwnership()) {
        return false; // Unable to reopen without having the path
    }
    releaseFileData();
    return true;
}

const std::string_view& FileSource::getFileData() const {
    const_cast<FileSource*>(this)->load();
    return mFileData;
}

void FileSource::releaseFileData() const {
    if (mFileMemMapper) {
        mFileMemMapper.reset();
    }
    mFileData = std::string_view();
}

std::ostream& operator<<(std::ostream& stream, const FileSource& fileSource) {
    stream << "<FileSource: " << (fileSource.empty() ? "None" : fileSource.getName()) << ">";
    return stream;
}