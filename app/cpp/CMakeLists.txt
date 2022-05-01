cmake_minimum_required(VERSION 3.4.1)
add_library(
        app
        SHARED
        ./kill_tor.cpp)
find_library(
        log-lib
        log)

target_link_libraries(
        app
        ${log-lib})