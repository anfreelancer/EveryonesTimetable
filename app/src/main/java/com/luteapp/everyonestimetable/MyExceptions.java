package com.luteapp.everyonestimetable;

/**
 * Common base class for all my exceptions so I can catch only mine if I want to.
 * ET is short for Everyone's Timetable.
 */
class ETException extends Exception
{
    private static final long serialVersionUID = 3731992303902683840L;

    public ETException(String msg, Exception cause)
    {
        super(msg, cause);
    }
}

class EncryptionException extends ETException
{
    private static final long serialVersionUID = -5426024696198089343L;
    
    public EncryptionException(String msg, Exception cause)
    {
        super(msg, cause);
    }
}

class ServerCommException extends ETException
{
    private static final long serialVersionUID = 8354312333711406062L;

    public ServerCommException(String msg, Exception cause)
    {
        super(msg, cause);
    }
}
