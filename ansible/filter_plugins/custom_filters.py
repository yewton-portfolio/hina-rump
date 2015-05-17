# -*- coding: utf-8 -*-

def surround(data, by):
    return '{0}{1}{0}'.format(by, data)

def surround_by_quote(data):
    return surround(data, "'")

def prefixed(data, prefix):
    return ['{0}{1}'.format(prefix, elt) for elt in data]

class FilterModule (object):
    def filters(self):
        return {
            'surround': surround,
            'surround_by_quote': surround_by_quote,
            'prefixed': prefixed
        }
