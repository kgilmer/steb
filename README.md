# STEB - Shell to Linux Bridge

STEB allows for easy shell integration
with Eclipse.  Rather than toil with workspaces and File->Open...
drugery, launch an Eclipse editor on the command line as you
would vi or emacs.  Steb supports multiple concurrent sessions,
and can create projects from directories automatically.

## Release 0.7.0

- Use the Eclipse "Compare Files" (AKA diff) feature.  Usage: `steb -c file1 file2`

## STEB shell script

```
#!/bin/bash
# Client script for Linux Shell Eclipse Bridge (steb)
# Set STEBPORT to a specific port.  In shell session you can then
# export this value to allow for multiple shell/eclipse sessions to exist peacefully

if [ -z $STEBPORT ]; then
        STEBPORT=4408
fi

proj_flag=

while getopts 'pc' OPTION
do
  case $OPTION in 
  p)    proj_flag=1
                    ;;
  c)    diff_flag=1
                    ;;
  esac 
done

shift $(($OPTIND - 1))
if [ ! -f $1 ]
then
        touch $1
fi

if [ "$proj_flag" ]
then
    echo "-p `realpath $1`" | nc localhost $STEBPORT
elif [ "$diff_flag" ]
then
    echo "-c `realpath $1` `realpath $2`" | nc localhost $STEBPORT
else 
    echo "`realpath $1`" | nc localhost $STEBPORT
fi
``` 

## Install

The updatesite for steb is http://kgilmer.github.com/steb/updatesite.
