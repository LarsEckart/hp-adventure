module Util exposing (posixToIso)

import String
import Time exposing (Month(..), Posix)

posixToIso : Posix -> String
posixToIso posix =
    let
        zone =
            Time.utc

        year =
            Time.toYear zone posix

        month =
            monthToInt (Time.toMonth zone posix)

        day =
            Time.toDay zone posix

        hour =
            Time.toHour zone posix

        minute =
            Time.toMinute zone posix

        second =
            Time.toSecond zone posix
    in
    String.fromInt year
        ++ "-"
        ++ pad2 month
        ++ "-"
        ++ pad2 day
        ++ "T"
        ++ pad2 hour
        ++ ":"
        ++ pad2 minute
        ++ ":"
        ++ pad2 second
        ++ "Z"


pad2 : Int -> String
pad2 value =
    let
        text =
            String.fromInt value
    in
    if String.length text < 2 then
        "0" ++ text

    else
        text


monthToInt : Month -> Int
monthToInt month =
    case month of
        Jan ->
            1

        Feb ->
            2

        Mar ->
            3

        Apr ->
            4

        May ->
            5

        Jun ->
            6

        Jul ->
            7

        Aug ->
            8

        Sep ->
            9

        Oct ->
            10

        Nov ->
            11

        Dec ->
            12
