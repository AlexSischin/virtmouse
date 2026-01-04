# About

A CLI app that performs human-like mouse actions sequentially.

# Build

```shell
javac src/VirtualMouse.java -d out/production/virtmouse
jar --create --file=VirtualMouse.jar --main-class=VirtualMouse -C out/production/virtmouse .

```

# Usage

    VirtualMouse - perform mouse actions sequentially with randomized delays
    
    Usage:
      java -jar VirtualMouse.jar [options] --action <ACTION> [--action <ACTION> ...]
    
    Options:
      --action <ACTION>        Add an action to execute. May be specified multiple times.
                               ACTION formats:
                                 click
                                 move <x> <y>
    
      --min-delay-ms <ms>      Minimum random delay between actions in milliseconds.
                               Default: 50
    
      --max-delay-ms <ms>      Maximum random delay between actions in milliseconds.
                               Default: 200
    
      -h, --help               Show this help and exit.
    
    Exit codes:
      0  Success
      1  Usage error (invalid arguments/options)
      2  Runtime error

    Examples:
      java -jar VirtualMouse.jar --action "move 1000 500" --action "click"
