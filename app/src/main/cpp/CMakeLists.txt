cmake_minimum_required(VERSION 3.22.1)
project(athea LANGUAGES C)

add_library(athea SHARED pty.c)
