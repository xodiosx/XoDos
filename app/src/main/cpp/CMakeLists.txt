cmake_minimum_required(VERSION 3.18.1)

project("termux_native" LANGUAGES C CXX ASM)

# -----------------------------------------------------------------------------
# ⚙️ Disable Termux Bootstrap temporarily (zip missing)
# -----------------------------------------------------------------------------
# add_library(termux-bootstrap SHARED
#     termux-bootstrap.c
#     termux-bootstrap-zip.S
# )
# find_library(log-lib log)
# find_library(z-lib z)
# target_link_libraries(termux-bootstrap ${log-lib} ${z-lib})

# -----------------------------------------------------------------------------
# ✅ Build Xbox ISO Wrapper only
# -----------------------------------------------------------------------------
add_library(xbox_iso_wrapper SHARED
    newxbox_iso_wrapper.cpp
)

find_library(log-lib log)
find_library(z-lib z)
find_library(android-lib android)

target_include_directories(xbox_iso_wrapper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(xbox_iso_wrapper
    ${log-lib}
    ${z-lib}
    ${android-lib}
)

set_target_properties(xbox_iso_wrapper PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

target_compile_options(xbox_iso_wrapper PRIVATE
    -fexceptions
    -frtti
    -Wall
    -Wextra
    -O2
)

target_link_options(xbox_iso_wrapper PRIVATE
    -Wl,--gc-sections
)