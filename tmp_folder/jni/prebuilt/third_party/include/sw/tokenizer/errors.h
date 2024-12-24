/**************************************************************************
   Copyright (c) 2023 sewenew

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 *************************************************************************/

#ifndef SEWENEW_TOKENIZER_ERRORS_H
#define SEWENEW_TOKENIZER_ERRORS_H

#include <exception>
#include <string>

namespace sw::tokenizer {

class Error : public std::exception {
public:
    explicit Error(const std::string &msg) : _msg(msg) {}

    Error(const Error &) = default;
    Error& operator=(const Error &) = default;

    Error(Error &&) = default;
    Error& operator=(Error &&) = default;

    virtual ~Error() override = default;

    virtual const char* what() const noexcept override {
        return _msg.data();
    }

private:
    std::string _msg;
};

}

#endif // end SEWENEW_TOKENIZER_ERRORS_H
