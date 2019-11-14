/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "tri-state-key-calibrate_daemon"

#include <android-base/logging.h>
#include <unistd.h>
#include <fstream>
#include <sstream>
#include <vector>

struct word_reader : std::ctype<char> {
    word_reader(std::string const &delims) : std::ctype<char>(get_table(delims)) {}
    static std::ctype_base::mask const* get_table(std::string const &delims) {
        static std::vector<std::ctype_base::mask> rc(table_size, std::ctype_base::mask());

        for (char ch : delims)
            rc[ch] = std::ctype_base::space;
        return &rc[0];
    }
};

int main() {
    std::ifstream persist_file("/mnt/vendor/persist/engineermode/tri_state_hall_data");
    std::ofstream sys_file("/sys/bus/platform/devices/soc:tri_state_key/hall_data_calib");
    std::string hallData;
    std::string calibData;
    std::string newCalibrationData;

    persist_file >> hallData;
    persist_file.close();

    if (!hallData.empty()) {
        std::istringstream in(hallData);
        in.imbue(std::locale(std::locale(), new word_reader(",;")));
        while (in >> calibData)
            newCalibrationData.append(calibData + ",");
        newCalibrationData.erase(std::prev(newCalibrationData.end()));
        sys_file << newCalibrationData;
        sys_file.close();
    }
    return 0;
}
