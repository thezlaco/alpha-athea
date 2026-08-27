cmake_minimum_required(VERSION 3.22.1)
project(athea LANGUAGES C)

add_library(athea SHARED pty.c)
target_compile_options(athea PRIVATE -O3 -flto -ffunction-sections -fdata-sections)
target_link_options(athea PRIVATE -Wl,--gc-sections -s -flto)
