module Model exposing
    ( Adventure
    , AssistantTurn
    , AuthState(..)
    , CompletedAdventure
    , GameState
    , ImageData
    , Item
    , Player
    , Stats
    , Turn
    , defaultState
    , isAdventureCompleted
    , isProfileComplete
    )

type alias Item =
    { name : String
    , description : String
    , foundAt : String
    }


type alias CompletedAdventure =
    { title : String
    , summary : String
    , completedAt : String
    }


type alias Stats =
    { adventuresCompleted : Int
    , totalTurns : Int
    }


type AuthState
    = NeedsPassword
    | Validating
    | Authenticated

type alias Player =
    { name : String
    , houseName : String
    , inventory : List Item
    , completedAdventures : List CompletedAdventure
    , stats : Stats
    }


type alias ImageData =
    { mimeType : String
    , base64 : String
    , prompt : Maybe String
    }


type alias AssistantTurn =
    { storyText : String
    , suggestedActions : List String
    , newItems : List Item
    , adventureCompleted : Bool
    , image : Maybe ImageData
    }


type alias Turn =
    { userAction : String
    , assistant : Maybe AssistantTurn
    }


type alias Adventure =
    { title : Maybe String
    , startedAt : String
    , turns : List Turn
    }


type alias GameState =
    { schemaVersion : Int
    , player : Player
    , currentAdventure : Maybe Adventure
    , actionInput : String
    , isLoading : Bool
    , isOnline : Bool
    , error : Maybe String
    , notice : Maybe String
    , showInventory : Bool
    , showHistory : Bool
    , pendingAbandon : Bool
    , authState : AuthState
    , passwordInput : String
    }


defaultState : GameState
defaultState =
    { schemaVersion = 2
    , player =
        { name = ""
        , houseName = ""
        , inventory = []
        , completedAdventures = []
        , stats = { adventuresCompleted = 0, totalTurns = 0 }
        }
    , currentAdventure = Nothing
    , actionInput = ""
    , isLoading = False
    , isOnline = True
    , error = Nothing
    , notice = Nothing
    , showInventory = True
    , showHistory = True
    , pendingAbandon = False
    , authState = NeedsPassword
    , passwordInput = ""
    }


isProfileComplete : Player -> Bool
isProfileComplete player =
    String.trim player.name /= "" && String.trim player.houseName /= ""


isAdventureCompleted : Adventure -> Bool
isAdventureCompleted adventure =
    case List.reverse adventure.turns of
        [] ->
            False

        lastTurn :: _ ->
            case lastTurn.assistant of
                Nothing ->
                    False

                Just assistant ->
                    assistant.adventureCompleted
