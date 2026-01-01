module Api exposing (HttpError, StoryResponse, errorToString, sendStory)

import Http
import Json.Decode as Decode exposing (Decoder)
import Json.Encode as Encode
import Model

type alias HttpError =
    Http.Error


type alias StoryResponse =
    { assistant : Assistant
    }


type alias Assistant =
    { storyText : String
    , suggestedActions : List String
    , newItems : List Model.Item
    , adventure : AdventureMeta
    }


type alias AdventureMeta =
    { title : Maybe String
    , completed : Bool
    , summary : Maybe String
    , completedAt : Maybe String
    }




sendStory : Model.GameState -> String -> (Result HttpError StoryResponse -> msg) -> Cmd msg
sendStory state action toMsg =
    Http.post
        { url = "/api/story"
        , body = Http.jsonBody (encodeStoryRequest state action)
        , expect = Http.expectJson toMsg decodeStoryResponse
        }


errorToString : HttpError -> String
errorToString error =
    case error of
        Http.BadUrl _ ->
            "Die Anfrage war ungültig."

        Http.Timeout ->
            "Die Anfrage hat zu lange gedauert."

        Http.NetworkError ->
            "Netzwerkfehler. Bitte prüfe deine Verbindung."

        Http.BadStatus _ ->
            "Der Server hat mit einem Fehler geantwortet."

        Http.BadBody _ ->
            "Die Antwort konnte nicht gelesen werden."


encodeStoryRequest : Model.GameState -> String -> Encode.Value
encodeStoryRequest state action =
    Encode.object
        [ ( "player", encodePlayer state.player )
        , ( "currentAdventure", encodeMaybe encodeAdventure state.currentAdventure )
        , ( "conversationHistory", Encode.list encodeChatMessage (historyFromAdventure state.currentAdventure) )
        , ( "action", Encode.string action )
        ]


encodePlayer : Model.Player -> Encode.Value
encodePlayer player =
    Encode.object
        [ ( "name", Encode.string player.name )
        , ( "houseName", Encode.string player.houseName )
        , ( "inventory", Encode.list encodeItem player.inventory )
        , ( "completedAdventures", Encode.list encodeCompletedAdventure player.completedAdventures )
        , ( "stats", encodeStats player.stats )
        ]


encodeAdventure : Model.Adventure -> Encode.Value
encodeAdventure adventure =
    Encode.object
        [ ( "title", encodeMaybe Encode.string adventure.title )
        , ( "startedAt", Encode.string adventure.startedAt )
        ]


encodeChatMessage : ChatMessage -> Encode.Value
encodeChatMessage message =
    Encode.object
        [ ( "role", Encode.string message.role )
        , ( "content", Encode.string message.content )
        ]


type alias ChatMessage =
    { role : String
    , content : String
    }


historyFromAdventure : Maybe Model.Adventure -> List ChatMessage
historyFromAdventure maybeAdventure =
    case maybeAdventure of
        Nothing ->
            []

        Just adventure ->
            List.concatMap turnToMessages adventure.turns


turnToMessages : Model.Turn -> List ChatMessage
turnToMessages turn =
    case turn.assistant of
        Nothing ->
            [ { role = "user", content = turn.userAction } ]

        Just assistant ->
            [ { role = "user", content = turn.userAction }
            , { role = "assistant", content = assistant.storyText }
            ]


decodeStoryResponse : Decoder StoryResponse
decodeStoryResponse =
    Decode.map StoryResponse (Decode.field "assistant" assistantDecoder)


assistantDecoder : Decoder Assistant
assistantDecoder =
    Decode.map4 Assistant
        (Decode.field "storyText" Decode.string)
        (Decode.oneOf
            [ Decode.field "suggestedActions" (Decode.list Decode.string)
            , Decode.succeed []
            ]
        )
        (Decode.oneOf
            [ Decode.field "newItems" (Decode.list itemDecoder)
            , Decode.succeed []
            ]
        )
        (Decode.oneOf
            [ Decode.field "adventure" adventureDecoder
            , Decode.succeed defaultAdventure
            ]
        )


adventureDecoder : Decoder AdventureMeta
adventureDecoder =
    Decode.map4 AdventureMeta
        (Decode.maybe (Decode.field "title" Decode.string))
        (Decode.oneOf
            [ Decode.field "completed" Decode.bool
            , Decode.succeed False
            ]
        )
        (Decode.maybe (Decode.field "summary" Decode.string))
        (Decode.maybe (Decode.field "completedAt" Decode.string))


defaultAdventure : AdventureMeta
defaultAdventure =
    { title = Nothing
    , completed = False
    , summary = Nothing
    , completedAt = Nothing
    }


encodeItem : Model.Item -> Encode.Value
encodeItem item =
    Encode.object
        [ ( "name", Encode.string item.name )
        , ( "description", Encode.string item.description )
        , ( "foundAt", Encode.string item.foundAt )
        ]


itemDecoder : Decoder Model.Item
itemDecoder =
    Decode.map3 Model.Item
        (Decode.field "name" Decode.string)
        (Decode.field "description" Decode.string)
        (Decode.field "foundAt" Decode.string)


encodeCompletedAdventure : Model.CompletedAdventure -> Encode.Value
encodeCompletedAdventure adventure =
    Encode.object
        [ ( "title", Encode.string adventure.title )
        , ( "summary", Encode.string adventure.summary )
        , ( "completedAt", Encode.string adventure.completedAt )
        ]


encodeStats : Model.Stats -> Encode.Value
encodeStats stats =
    Encode.object
        [ ( "adventuresCompleted", Encode.int stats.adventuresCompleted )
        , ( "totalTurns", Encode.int stats.totalTurns )
        ]


encodeMaybe : (a -> Encode.Value) -> Maybe a -> Encode.Value
encodeMaybe encoder maybeValue =
    case maybeValue of
        Nothing ->
            Encode.null

        Just value ->
            encoder value
