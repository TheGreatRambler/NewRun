UNAME := $(shell uname -o)

# No extension for the executable
TARGET_EXEC ?= newrun_server

BUILD_DIR ?= ./build
SRC_DIRS ?= ./source

# Set compilers to MinGW64 compilers
CC := gcc
CXX := c++

# C flags
CFLAGS := -std=gnu11
# C++ flags
CXXFLAGS := -std=gnu++17 $(shell pkg-config --cflags libssl libcurl zlib)
# C/C++ flags (no -pendantic)
CPPFLAGS := -Wall -Wextra -Wno-cast-function-type -I./include -I./source

ifeq ($(BUILD),release)
	# "Release" build - optimization, and no debug symbols
	CPPFLAGS += -O3 -s -DNDEBUG
else
	# "Debug" build - no optimization, and debugging symbols
	CPPFLAGS += -Og -g -ggdb -DDEBUG
endif

LDFLAGS := -lpthread -lcrypto $(shell pkg-config --libs libssl libcurl zlib)

ifeq ($(UNAME),Msys)
# Needed for sockets on windows
	LDFLAGS += -lmingw32 -lws2_32 -lwsock32
else
	LDFLAGS += -ldl
endif

SRCS := $(shell find $(SRC_DIRS) -name *.cpp -or -name *.c -or -name *.s)
OBJS := $(SRCS:%=$(BUILD_DIR)/%.o)
DEPS := $(OBJS:.o=.d)

INC_DIRS := $(shell find $(SRC_DIRS) -type d)
INC_FLAGS := $(addprefix -I,$(INC_DIRS))

CPPFLAGS ?= $(INC_FLAGS) -MMD -MP

$(BUILD_DIR)/$(TARGET_EXEC): $(OBJS)
	$(CXX) $(OBJS) -o $@ $(LDFLAGS)

# assembly
$(BUILD_DIR)/%.s.o: %.s
	$(MKDIR_P) $(dir $@)
	$(AS) $(ASFLAGS) -c $< -o $@

# c source
$(BUILD_DIR)/%.c.o: %.c
	$(MKDIR_P) $(dir $@)
	$(CC) $(CPPFLAGS) $(CFLAGS) -c $< -o $@

# c++ source
$(BUILD_DIR)/%.cpp.o: %.cpp
	$(MKDIR_P) $(dir $@)
	$(CXX) $(CPPFLAGS) $(CXXFLAGS) -c $< -o $@


.PHONY: clean

clean:
	$(RM) -r $(BUILD_DIR)

-include $(DEPS)

MKDIR_P ?= mkdir -p
