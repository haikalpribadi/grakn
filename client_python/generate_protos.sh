#!/usr/bin/env bash

sed -i.bak -e 's@protocol/session@grakn/service/Session/autogenerated@g' $1
mv $1 $2